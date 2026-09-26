// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Après un reboot du téléphone ET après une mise à jour de l'app, Android arrête les services et ne
 * les relance pas.
 *
 * Dashcam : un service caméra ne peut pas être relancé ici (interdit depuis l'arrière-plan), on
 * prévient l'utilisateur par une notification qui la réarme d'un appui.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        if (Prefs.getDashcamArmed(context)) {
            EventLog.log(context, "[dashcam] DESARMEE par ${intent.action} — notification de réarmement")
            DashcamService.postRearmNotification(context)
        }
    }
}
