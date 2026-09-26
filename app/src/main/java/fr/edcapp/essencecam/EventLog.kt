// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal d'événements des services, lisible en debug via
 * `adb exec-out run-as <applicationId> cat files/events.log` — sert à comprendre après coup un
 * comportement constaté en conduite (alerte doublée, connexion AA qui saute, service tué par
 * Android...). Tronqué au-delà de 100 Ko. Ne doit jamais perturber un service : toute erreur est avalée.
 */
object EventLog {
    fun log(context: Context, message: String) {
        try {
            val f = File(context.filesDir, "events.log")
            if (f.length() > 100_000) f.writeText("")
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
            f.appendText("$ts $message\n")
        } catch (e: Exception) {
            // Volontairement ignoré.
        }
    }
}
