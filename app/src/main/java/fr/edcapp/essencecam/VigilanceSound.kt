// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Alerte sonore de la zone de vigilance : sonnerie système par défaut de type alarme, jouée une
 * seule fois (les sonneries d'alarme bouclent par défaut, on force l'arrêt sans boucle), sur le
 * flux ALARME pour rester audible en mode silencieux sans permission "Ne pas déranger". Pas de
 * sélecteur de son ni de réglage de volume dans cette version — juste un interrupteur (voir
 * Prefs.getVigilanceSoundEnabled).
 */
object VigilanceSound {
    private var current: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStop: Runnable? = null

    fun play(context: Context, durationMs: Long = 4000L) {
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.isLooping = false
        }
        current = ringtone
        ringtone.play()

        val runnable = Runnable { stop() }
        pendingStop = runnable
        handler.postDelayed(runnable, durationMs)
    }

    fun stop() {
        pendingStop?.let { handler.removeCallbacks(it) }
        pendingStop = null
        current?.let { if (it.isPlaying) it.stop() }
        current = null
    }
}
