// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Enregistrement vidéo en boucle (façon dashcam), caméra arrière, sans son (voir CLAUDE.md pour
 * le choix "pas de micro"). Deux modes :
 * - **Manuel** (ACTION_START) : enregistre dès le démarrage.
 * - **Auto** (ACTION_START_AUTO) : reste en veille (caméra non ouverte) tant que le téléphone
 *   n'est pas connecté à Android Auto, démarre/arrête l'enregistrement selon la connexion.
 *
 * Contrairement à la localisation, il n'existe AUCUNE permission "arrière-plan" pour la caméra :
 * le mode auto ne peut donc PAS se réarmer après un reboot du téléphone (un FGS de type "camera"
 * est explicitement interdit au démarrage depuis un BroadcastReceiver BOOT_COMPLETED sur Android
 * 15+, vérifié sur la doc officielle) — BootReceiver prévient par une notification à toucher.
 * Le mode manuel s'arrête sur fermeture complète de l'app (onTaskRemoved), le mode auto
 * n'est PAS coupé (doit survivre au swipe pour rester utile).
 *
 * Stockage : /storage/emulated/0/Dashcam (pas de carte SD sur le S22+, tout va sur le stockage
 * interne partagé) — clips de durée fixe (Prefs.getDashcamClipMinutes), les plus anciens sont
 * supprimés automatiquement dès que le dossier dépasse Prefs.getDashcamStorageCapGb.
 */
class DashcamService : LifecycleService() {

    companion object {
        const val ACTION_START = "fr.edcapp.essencecam.action.START_DASHCAM"
        const val ACTION_START_AUTO = "fr.edcapp.essencecam.action.START_DASHCAM_AUTO"
        const val ACTION_STOP = "fr.edcapp.essencecam.action.STOP_DASHCAM"
        private const val CHANNEL_ID = "dashcam_monitor"
        private const val NOTIF_ID = 2001
        // Notification distincte (pas celle du service) : postée quand Android a tué le processus,
        // car un service caméra ne peut pas être relancé seul depuis l'arrière-plan — mais un appui
        // sur une notification est une interaction utilisateur autorisée à le démarrer.
        private const val CHANNEL_REARM_ID = "dashcam_rearm"
        private const val NOTIF_REARM_ID = 2002

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isAutoMode: Boolean = false
            private set

        /** Dernier statut affiché (attente / enregistrement / erreur), lu par l'onglet Dash cam. */
        @Volatile
        var statusText: String = ""
            private set

        /** Nom du clip en cours d'enregistrement (null à l'arrêt) : la liste des vidéos ne doit pas le supprimer. */
        @Volatile
        var currentClipName: String? = null
            private set

        /** Posé par l'onglet Dash cam tant qu'il est visible, pour suivre le statut en direct. */
        @Volatile
        var statusListener: ((String) -> Unit)? = null

        /**
         * "Dashcam désarmée — touche pour réarmer". Appelée quand la dashcam devrait tourner mais ne
         * tourne plus (redémarrage du téléphone, mise à jour de l'app, kill non récupérable). Un
         * service caméra ne peut pas se relancer seul depuis l'arrière-plan sur Android 14+, mais
         * l'appui sur une notification est une interaction utilisateur autorisée à le démarrer.
         */
        fun postRearmNotification(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REARM_ID, context.getString(R.string.dashcam_notif_rearm_title), NotificationManager.IMPORTANCE_HIGH),
            )
            val action = if (Prefs.getDashcamAutoAA(context)) ACTION_START_AUTO else ACTION_START
            val intent = Intent(context, DashcamService::class.java).setAction(action)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getForegroundService(context, 1, intent, flags)
            val notification = NotificationCompat.Builder(context, CHANNEL_REARM_ID)
                .setContentTitle(context.getString(R.string.dashcam_notif_rearm_title))
                .setContentText(context.getString(R.string.dashcam_notif_rearm_text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pending)
                .addAction(0, context.getString(R.string.dashcam_notif_rearm_action), pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(NOTIF_REARM_ID, notification)
        }
    }

    private var recordingActive = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var carConnection: CarConnection? = null
    private val carConnectionObserver = Observer<Int> { type -> onCarConnectionType(type) }
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var clipTimeoutRunnable: Runnable? = null
    private var carConnected = false
    private var thermalPaused = false
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopDashcam()
                return START_NOT_STICKY
            }
            ACTION_START_AUTO -> startDashcam(auto = true)
            ACTION_START -> startDashcam(auto = false)
            // Redémarrage par le système après un kill/crash (intent null) : on retrouve le mode
            // d'origine (la case est verrouillée pendant une session, donc Prefs le reflète).
            else -> startDashcam(auto = Prefs.getDashcamAutoAA(this))
        }
        return START_STICKY
    }

    private fun startDashcam(auto: Boolean) {
        if (isRunning) return
        isRunning = true
        isAutoMode = auto

        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID,
                buildNotification(if (auto) getString(R.string.dashcam_status_waiting) else getString(R.string.dashcam_status_initializing)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } catch (e: Exception) {
            // Un FGS "camera" ne peut pas démarrer depuis l'arrière-plan (cas d'un redémarrage
            // système sans activité visible) : on s'arrête proprement plutôt que de planter, et on
            // propose de réarmer d'un appui (interaction notification = démarrage autorisé).
            isRunning = false
            isAutoMode = false
            statusText = ""
            EventLog.log(this, "[dashcam] REDEMARRAGE SYSTEME impossible depuis l'arriere-plan (${e.javaClass.simpleName}) armee=${Prefs.getDashcamArmed(this)}")
            if (Prefs.getDashcamArmed(this)) postRearmNotification(this)
            stopSelf()
            return
        }

        Prefs.setDashcamArmed(this, true)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_REARM_ID)
        EventLog.log(this, "[dashcam] DEMARRAGE auto=$auto")
        setStatus(if (auto) getString(R.string.dashcam_status_waiting) else getString(R.string.dashcam_status_initializing))

        registerThermalListener()
        if (auto) {
            carConnection = CarConnection(this).also {
                it.type.observeForever(carConnectionObserver)
            }
        } else {
            beginRecording()
        }
    }

    /**
     * Pause automatique quand Android signale une surchauffe sévère (THERMAL_STATUS_SEVERE ou pire),
     * reprise quand le statut redescend à modéré ou moins. API 29+ : avant, aucune protection.
     */
    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val power = getSystemService(PowerManager::class.java) ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status -> handler.post { onThermalStatus(status) } }
        thermalListener = listener
        power.addThermalStatusListener(ContextCompat.getMainExecutor(this), listener)
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        thermalListener?.let { getSystemService(PowerManager::class.java)?.removeThermalStatusListener(it) }
        thermalListener = null
        thermalPaused = false
    }

    private fun onThermalStatus(status: Int) {
        if (!isRunning) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            if (thermalPaused) return
            thermalPaused = true
            EventLog.log(this, "[dashcam] PAUSE thermique statut=$status")
            if (recordingActive) endRecording()
            setStatus(getString(R.string.dashcam_status_hot))
        } else if (status <= PowerManager.THERMAL_STATUS_MODERATE && thermalPaused) {
            thermalPaused = false
            EventLog.log(this, "[dashcam] REPRISE thermique statut=$status")
            if (!isAutoMode || carConnected) beginRecording() else setStatus(getString(R.string.dashcam_status_waiting))
        }
    }

    private fun onCarConnectionType(type: Int) {
        val connected = type == CarConnection.CONNECTION_TYPE_PROJECTION
        EventLog.log(this, "[dashcam] AA type=$type connecte=$connected")
        carConnected = connected
        if (connected) beginRecording() else {
            endRecording()
            if (thermalPaused) setStatus(getString(R.string.dashcam_status_waiting))
        }
    }

    private fun beginRecording() {
        if (recordingActive) return
        if (thermalPaused) {
            setStatus(getString(R.string.dashcam_status_hot))
            return
        }
        val permOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!permOk) {
            setStatus(getString(R.string.dashcam_status_no_camera_permission))
            return
        }
        // Sans accès en écriture, CameraX ne renvoie pas d'erreur récupérable : il lève une
        // AssertionError sur son thread interne qui fait planter TOUTE l'app.
        if (!storageWritable()) {
            setStatus(getString(R.string.dashcam_status_no_storage))
            return
        }
        // Libère d'abord ce qui peut l'être (plafond, espace libre) ; refuse de démarrer s'il manque encore de la place.
        if (!enforceStorageCap()) {
            EventLog.log(this, "[dashcam] REFUS espace libre insuffisant (${dashcamDir().usableSpace / 1_000_000} Mo)")
            setStatus(getString(R.string.dashcam_status_no_space))
            return
        }
        recordingActive = true
        EventLog.log(this, "[dashcam] REC debut qualite=${Prefs.getDashcamQuality(this)}")
        setupCameraAndRecord()
    }

    private fun storageWritable(): Boolean {
        val dir = dashcamDir()
        return dir.isDirectory && dir.canWrite()
    }

    private fun endRecording() {
        if (!recordingActive) return
        recordingActive = false
        currentClipName = null
        clipTimeoutRunnable?.let { handler.removeCallbacks(it) }
        clipTimeoutRunnable = null
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        videoCapture = null
        EventLog.log(this, "[dashcam] REC fin")
        setStatus(getString(R.string.dashcam_status_waiting))
    }

    private fun stopDashcam() {
        isRunning = false
        isAutoMode = false
        Prefs.setDashcamArmed(this, false)
        EventLog.log(this, "[dashcam] ARRET demande")
        endRecording()
        unregisterThermalListener()
        carConnection?.type?.removeObserver(carConnectionObserver)
        carConnection = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Mode manuel : coupe sur fermeture de l'app. Mode auto : ne coupe pas, doit survivre au
     * swipe pour rester utile. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isAutoMode) {
            stopDashcam()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun setupCameraAndRecord() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val quality = when (Prefs.getDashcamQuality(this)) {
                    "SD" -> Quality.SD
                    "FHD" -> Quality.FHD
                    else -> Quality.HD
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(quality))
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                videoCapture = vc
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vc)
                startNextClip()
            } catch (e: Exception) {
                EventLog.log(this, "[dashcam] ERREUR camera ${e.message ?: e.toString()}")
                setStatus(getString(R.string.dashcam_status_camera_error, e.message ?: e.toString()))
                recordingActive = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Démarre un clip, programme son arrêt après la durée configurée. Le clip SUIVANT n'est
     * enchaîné que dans le callback VideoRecordEvent.Finalize (pas juste après avoir appelé
     * stop()) : CameraX exige que l'enregistrement précédent soit réellement terminé avant d'en
     * démarrer un nouveau sur le même Recorder, sous peine d'IllegalStateException.
     */
    private fun startNextClip() {
        val vc = videoCapture ?: return
        val file = File(dashcamDir(), SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(java.util.Date()) + ".mp4")
        val outputOptions = FileOutputOptions.Builder(file).build()
        currentClipName = file.name

        activeRecording = vc.output.prepareRecording(this, outputOptions)
            .start(cameraExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val spaceOk = enforceStorageCap()
                    if (!spaceOk && recordingActive) {
                        handler.post {
                            if (recordingActive) {
                                endRecording()
                                EventLog.log(this, "[dashcam] ARRET espace libre insuffisant (${dashcamDir().usableSpace / 1_000_000} Mo)")
                                setStatus(getString(R.string.dashcam_status_no_space))
                            }
                        }
                    } else if (event.hasError()) {
                        // Pas d'enchaînement sur erreur : réessayer en boucle serrée saturerait
                        // caméra et encodeur (chauffe) sans jamais produire de fichier valide.
                        handler.post {
                            // Erreur due à un arrêt volontaire (déconnexion AA, bouton) : rien à signaler.
                            if (recordingActive) {
                                endRecording()
                                EventLog.log(this, "[dashcam] ERREUR enregistrement code=${event.error}")
                                setStatus(getString(R.string.dashcam_status_record_error, event.error))
                            }
                        }
                    } else if (recordingActive) {
                        handler.post { startNextClip() }
                    }
                }
            }

        setStatus(getString(R.string.dashcam_status_recording, file.name))
        val clipMs = Prefs.getDashcamClipMinutes(this).coerceAtLeast(1) * 60_000L
        val runnable = Runnable { activeRecording?.stop() }
        clipTimeoutRunnable = runnable
        handler.postDelayed(runnable, clipMs)
    }

    private fun dashcamDir(): File {
        return DashcamFiles.dir(this)
    }

    /**
     * Supprime les plus anciens clips non protégés (plafond configuré ET espace libre minimal, voir
     * StoragePlan). Seuls les clips créés par l'app (DashcamFiles.isClip) sont comptés ou supprimés.
     * Renvoie false si l'espace libre reste sous le minimum (rien de plus à supprimer).
     */
    private fun enforceStorageCap(): Boolean {
        val dir = dashcamDir()
        val capBytes = Prefs.getDashcamStorageCapGb(this).coerceAtLeast(1) * 1_000_000_000L
        val files = DashcamFiles.clips(dir).sortedBy { it.name }
        val protectedNames = Prefs.getProtectedClips(this)
        // Oublie les protections des clips qui n'existent plus.
        val existing = files.map { it.name }.toSet()
        if (!existing.containsAll(protectedNames)) Prefs.setProtectedClips(this, protectedNames.intersect(existing))
        val infos = files.map { ClipInfo(it.name, it.length(), it.name in protectedNames) }
        val doomed = StoragePlan.toDelete(infos, capBytes, dir.usableSpace).toSet()
        files.filter { it.name in doomed }.forEach { it.delete() }
        return StoragePlan.canRecord(dir.usableSpace)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.dashcam_notif_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, DashcamService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dashcam_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.dashcam_notif_stop), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Statut unique : notification du service, écran de l'onglet Dash cam (via statusListener). */
    private fun setStatus(text: String) {
        statusText = text
        updateNotification(text)
        statusListener?.invoke(text)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
