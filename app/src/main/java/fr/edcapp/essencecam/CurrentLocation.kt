// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Quels fournisseurs de localisation interroger, dans quel ordre : logique pure, testée sans appareil. */
object LocationPlan {
    const val MAX_LAST_KNOWN_AGE_MINUTES = 10L

    /**
     * Le fournisseur "fused" du système (Android 12+) puis le fournisseur réseau (Wi-Fi et antennes) suffisent
     * avec la seule permission de position approximative. Le GPS exige la position précise : il n'est ajouté que
     * si elle a été accordée.
     */
    fun providers(sdkInt: Int, hasFine: Boolean): List<String> = buildList {
        if (sdkInt >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (hasFine) add(LocationManager.GPS_PROVIDER)
    }

    fun timeoutMs(provider: String): Long = if (provider == LocationManager.GPS_PROVIDER) 20_000L else 8_000L

    /** Une position déjà connue n'est acceptée que si elle est récente (jamais une position d'hier). */
    fun isFresh(ageNanos: Long): Boolean = ageNanos in 0..TimeUnit.MINUTES.toNanos(MAX_LAST_KNOWN_AGE_MINUTES)
}

/**
 * Une position ponctuelle via le service de localisation du système (LocationManager), sans bibliothèque
 * Google. Précision réseau : quelques dizaines à quelques centaines de mètres, suffisant pour chercher des
 * stations dans un rayon de 5 à 50 km.
 */
object CurrentLocation {

    /** La position, ou null si aucun fournisseur n'en donne (localisation coupée, aucun fournisseur disponible). */
    suspend fun get(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val providers = LocationPlan.providers(Build.VERSION.SDK_INT, hasFine).filter { usable(manager, it) }

        for (provider in providers) {
            val location = current(context, manager, provider)
            if (location != null) {
                EventLog.log(context, "[position] fournisseur=$provider")
                return location
            }
        }
        for (provider in providers) {
            val last = try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
            if (last != null && LocationPlan.isFresh(SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos)) {
                EventLog.log(context, "[position] dernière position connue, fournisseur=$provider")
                return last
            }
        }
        EventLog.log(context, "[position] aucune position (fournisseurs interrogés : $providers)")
        return null
    }

    private fun usable(manager: LocationManager, provider: String): Boolean =
        try {
            manager.getProvider(provider) != null && manager.isProviderEnabled(provider)
        } catch (e: Exception) {
            false
        }

    private suspend fun current(context: Context, manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(LocationPlan.timeoutMs(provider)) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
}
