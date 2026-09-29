// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.Observer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zone de vigilance (contrôle de vitesse) : service de premier plan, deux modes comme les autres
 * modules (manuel / auto lié à Android Auto). Voir VigilanceZone.kt pour le cadre légal et le choix
 * du rayon. N'affiche et ne communique jamais une position plus précise qu'une zone large : pas de
 * distance, pas de coordonnée, pas de type de contrôle — seulement "vous entrez dans une zone".
 *
 * Position par la seule permission approximative (ACCESS_COARSE_LOCATION), comme le reste de
 * l'édition publique (voir CurrentLocation/LocationPlan pour B10) : suffisant pour une zone de
 * plusieurs kilomètres, pas besoin de la position précise.
 *
 * Ce service ne se réarme pas après un redémarrage du téléphone (pas de BootReceiver) : cela
 * demanderait en plus ACCESS_BACKGROUND_LOCATION pour un cas d'usage secondaire. Après un reboot,
 * l'utilisateur rouvre l'app pour redémarrer le mode auto.
 */
class VigilanceService : Service() {

    companion object {
        const val ACTION_START = "fr.edcapp.essencecam.action.START_VIGILANCE"
        const val ACTION_START_AUTO = "fr.edcapp.essencecam.action.START_VIGILANCE_AUTO"
        const val ACTION_STOP = "fr.edcapp.essencecam.action.STOP_VIGILANCE"
        private const val CHANNEL_ID = "vigilance_monitor"
        private const val NOTIF_ID = 3001
        private const val TEXT_WAITING_CAR = "En attente de connexion Android Auto…"
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val RECLASSIFY_DISTANCE_M = 1_000f
        private const val RECLASSIFY_INTERVAL_MS = 120_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isAutoMode: Boolean = false
            private set

        @Volatile
        var statusText: String = ""
            private set

        /** Posé par l'onglet tant qu'il est visible, pour suivre le statut en direct. */
        @Volatile
        var statusListener: ((String) -> Unit)? = null
    }

    private var points: List<ControlPoint> = emptyList()
    private var inZone = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Classification "en agglomération" du dernier point connu (voir AgglomerationCheck) : resserre
     * le rayon à 500 m au lieu de 4 km. Reclassée seulement si la position a assez bougé ou après un
     * délai, pour limiter les appels réseau — jamais à chaque mise à jour de position (toutes les
     * 15 s). Tout échec repasse cette valeur à false (rayon large), jamais l'inverse. */
    private var isUrban = false
    private var lastClassifiedLocation: Location? = null
    private var lastClassifiedAtMs = 0L
    private var classifying = false

    /** true dès que la position est réellement sollicitée (manuel : toujours ; auto : seulement
     * pendant que CONNECTION_TYPE_PROJECTION est actif). */
    private var alerting = false
    private var carConnection: CarConnection? = null
    private val carConnectionObserver = Observer<Int> { type -> onCarConnectionType(type) }
    private val activeProviders = mutableListOf<String>()

    private val locationListener = LocationListenerCompat { location -> onNewLocation(location) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_START_AUTO -> startMonitoring(auto = true)
            ACTION_START -> startMonitoring(auto = false)
            else -> startMonitoring(auto = Prefs.getVigilanceAutoAA(this))
        }
        return START_STICKY
    }

    private fun startMonitoring(auto: Boolean) {
        if (isRunning) return
        isRunning = true
        isAutoMode = auto
        inZone = false
        isUrban = false
        lastClassifiedLocation = null
        lastClassifiedAtMs = 0L
        EventLog.log(this, "[vigilance] DEMARRAGE auto=$auto")

        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID,
                buildNotification(if (auto) TEXT_WAITING_CAR else "Chargement de la zone de vigilance…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            isRunning = false
            isAutoMode = false
            setStatus("")
            stopSelf()
            return
        }

        serviceScope.launch {
            points = withContext(Dispatchers.IO) { SpeedControlRepository.getPoints(this@VigilanceService) }
            if (auto) {
                carConnection = CarConnection(this@VigilanceService).also {
                    it.type.observeForever(carConnectionObserver)
                }
            } else {
                beginAlerting()
            }
        }
    }

    private fun onCarConnectionType(type: Int) {
        val connected = type == CarConnection.CONNECTION_TYPE_PROJECTION
        EventLog.log(this, "[vigilance] AA type=$type connecte=$connected")
        if (connected) beginAlerting() else endAlerting()
    }

    private fun beginAlerting() {
        if (alerting) return
        val locationOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!locationOk) {
            setStatus("Permission de localisation manquante — rouvre l'app pour la redonner")
            return
        }
        alerting = true
        setStatus(
            if (points.isEmpty()) "Zone de vigilance vide — réessaie plus tard"
            else "Zone de vigilance active — en attente de position",
        )
        val manager = getSystemService(LocationManager::class.java)
        if (manager == null) {
            setStatus("Service de localisation indisponible")
            return
        }
        val request = LocationRequestCompat.Builder(UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
            .build()
        for (provider in LocationPlan.providers(Build.VERSION.SDK_INT, hasFine = false)) {
            try {
                if (manager.getProvider(provider) == null || !manager.isProviderEnabled(provider)) continue
                LocationManagerCompat.requestLocationUpdates(manager, provider, request, ContextCompat.getMainExecutor(this), locationListener)
                activeProviders.add(provider)
            } catch (e: Exception) {
                // Fournisseur indisponible sur cet appareil : on continue avec les autres.
            }
        }
    }

    private fun endAlerting() {
        if (!alerting) return
        alerting = false
        removeLocationUpdates()
        inZone = false
        setStatus(TEXT_WAITING_CAR)
    }

    private fun removeLocationUpdates() {
        val manager = getSystemService(LocationManager::class.java)
        if (manager != null) {
            try {
                LocationManagerCompat.removeUpdates(manager, locationListener)
            } catch (e: Exception) {
                // Rien à faire si déjà retiré.
            }
        }
        activeProviders.clear()
    }

    private fun stopMonitoring() {
        isRunning = false
        isAutoMode = false
        alerting = false
        removeLocationUpdates()
        carConnection?.type?.removeObserver(carConnectionObserver)
        carConnection = null
        VigilanceSound.stop()
        EventLog.log(this, "[vigilance] ARRET demande")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Mode manuel : coupe sur fermeture de l'app. Mode auto : survit au swipe, comme les autres
     * modules — doit continuer à surveiller la connexion AA une fois l'app fermée. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isAutoMode) {
            stopMonitoring()
        }
        super.onTaskRemoved(rootIntent)
    }

    /** Jamais de distance, de coordonnée ni de type affiché : seulement l'entrée/sortie de zone. */
    private fun onNewLocation(location: Location) {
        maybeReclassifyUrban(location)
        if (points.isEmpty()) return
        val out = FloatArray(1)
        var nearestM = Double.MAX_VALUE
        for (point in points) {
            Location.distanceBetween(location.latitude, location.longitude, point.lat, point.lon, out)
            if (out[0] < nearestM) nearestM = out[0].toDouble()
        }
        val radius = if (isUrban) VigilanceZone.URBAN_RADIUS_METERS else VigilanceZone.RADIUS_METERS
        val exitMargin = if (isUrban) VigilanceZone.URBAN_EXIT_MARGIN_METERS else VigilanceZone.EXIT_MARGIN_METERS
        if (!inZone && VigilanceZone.isInZone(nearestM, radius)) {
            inZone = true
            EventLog.log(this, "[vigilance] ENTREE zone urbaine=$isUrban")
            alert()
        } else if (inZone && VigilanceZone.hasExitedZone(nearestM, exitMargin)) {
            inZone = false
            EventLog.log(this, "[vigilance] SORTIE zone")
        }
        setStatus(if (inZone) "⚠️ Zone de vigilance — contrôle de vitesse possible" else "Aucune zone de vigilance à proximité")
    }

    /**
     * Reclasse "en agglomération ?" au plus une fois toutes les RECLASSIFY_INTERVAL_MS, ou si la
     * position a bougé de plus de RECLASSIFY_DISTANCE_M depuis la dernière classification — jamais
     * à chaque position (appel réseau, voir AgglomerationCheck). Asynchrone : la classification en
     * cours ne bloque jamais le traitement de la position courante, qui utilise la dernière valeur
     * connue. Toute erreur réseau repasse isUrban à false (rayon large) dans AgglomerationCheck lui-même.
     */
    private fun maybeReclassifyUrban(location: Location) {
        if (classifying) return
        val now = System.currentTimeMillis()
        val last = lastClassifiedLocation
        val movedEnough = last == null || location.distanceTo(last) > RECLASSIFY_DISTANCE_M
        val timeUp = now - lastClassifiedAtMs > RECLASSIFY_INTERVAL_MS
        if (!movedEnough && !timeUp) return
        classifying = true
        lastClassifiedLocation = Location(location)
        lastClassifiedAtMs = now
        serviceScope.launch {
            val urban = withContext(Dispatchers.IO) {
                runCatching { AgglomerationCheck.isLikelyUrban(location.latitude, location.longitude) }.getOrDefault(false)
            }
            if (urban != isUrban) EventLog.log(this@VigilanceService, "[vigilance] zone urbaine=$urban")
            isUrban = urban
            classifying = false
        }
    }

    private fun alert() {
        if (Prefs.getVigilanceSoundEnabled(this)) {
            try {
                VigilanceSound.play(this)
            } catch (e: Exception) {
                // Son indisponible : on continue sans bloquer la surveillance.
            }
        }
    }

    private fun setStatus(text: String) {
        statusText = text
        updateNotification(text)
        statusListener?.invoke(text)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Zone de vigilance", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, VigilanceService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zone de vigilance active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Arrêter", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
