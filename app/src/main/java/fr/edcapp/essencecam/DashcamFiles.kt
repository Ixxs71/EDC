// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import java.io.File

/**
 * Les clips de la dashcam : uniquement les fichiers que l'app a créés (AAAAMMJJ_HHMMSS.mp4). Le dossier
 * dossier peut contenir autre chose : l'app ne doit jamais lister, compter ni SUPPRIMER un fichier qui
 * ne suit pas ce nom.
 *
 * Le dossier est celui de l'app sur le stockage externe (`Android/data/<app>/files/Dashcam`) : aucune
 * permission de stockage n'est nécessaire, il disparaît à la désinstallation.
 */
object DashcamFiles {
    private val CLIP_NAME = Regex("""\d{8}_\d{6}\.mp4""")

    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "Dashcam").also { if (!it.exists()) it.mkdirs() }
    }

    fun isClip(file: File): Boolean = file.isFile && CLIP_NAME.matches(file.name)

    fun clips(dir: File): List<File> = dir.listFiles()?.filter { isClip(it) } ?: emptyList()
}

/** Calculs de l'estimation « temps avant écrasement » de l'onglet Dash cam. */
object DashcamMath {
    /** Secondes d'enregistrement qui tiennent dans [capGb] Go au débit [bitsPerSecond] ; null si l'un est invalide. */
    fun retentionSeconds(capGb: Int, bitsPerSecond: Long): Long? {
        if (capGb <= 0 || bitsPerSecond <= 0) return null
        return capGb.toLong() * 1_000_000_000L * 8 / bitsPerSecond
    }

    /** "1 h 2 min" ou "45 min". */
    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "$hours h $minutes min" else "$minutes min"
    }
}

/** Un clip vu par le plan de stockage : nom, taille, protégé ou non. */
data class ClipInfo(val name: String, val size: Long, val isProtected: Boolean)

/**
 * Quels clips supprimer, logique pure et testée. Deux règles : le total des clips non protégés ne dépasse
 * pas le plafond configuré, ET l'espace libre du téléphone reste au moins [MIN_FREE_BYTES] (un
 * téléphone presque plein fait échouer l'enregistrement, même sous le plafond). Les clips protégés ne
 * sont jamais supprimés ni comptés dans le plafond.
 */
object StoragePlan {
    const val MIN_FREE_BYTES = 1_000_000_000L

    /** [clips] : du plus ancien au plus récent. Renvoie les noms à supprimer, le plus ancien d'abord. */
    fun toDelete(clips: List<ClipInfo>, capBytes: Long, freeBytes: Long, minFreeBytes: Long = MIN_FREE_BYTES): List<String> {
        val candidates = clips.filter { !it.isProtected }
        var total = candidates.sumOf { it.size }
        var free = freeBytes
        val result = mutableListOf<String>()
        for (clip in candidates) {
            if (total <= capBytes && free >= minFreeBytes) break
            total -= clip.size
            free += clip.size
            result += clip.name
        }
        return result
    }

    fun canRecord(freeBytes: Long, minFreeBytes: Long = MIN_FREE_BYTES): Boolean = freeBytes >= minFreeBytes
}
