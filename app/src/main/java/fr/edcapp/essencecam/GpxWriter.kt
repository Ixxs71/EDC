// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object GpxWriter {
    /** Marque les fichiers créés par l'app dans l'en-tête GPX : sert à ne supprimer que les siens. */
    const val CREATOR = "edc"

    private val STATION_FILES = FuelType.entries.map { "${it.apiField}_stations.gpx" }.toSet()

    /**
     * true pour un fichier GPX créé par l'app : nom de fichier de stations, ou en-tête portant
     * creator="edc". Ne supprime que ses propres fichiers, jamais un autre.
     */
    fun isOwnFile(file: File): Boolean {
        if (!file.isFile) return false
        if (file.name in STATION_FILES) return true
        if (!file.name.endsWith(".gpx", ignoreCase = true)) return false
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val head = CharArray(400)
                val n = reader.read(head)
                n > 0 && String(head, 0, n).contains("creator=\"$CREATOR\"")
            }
        }.getOrDefault(false)
    }

    /** Supprime les fichiers GPX créés par l'app dans [dir], et rien d'autre. */
    fun deleteOwnFiles(dir: File) {
        dir.listFiles()?.filter { isOwnFile(it) }?.forEach { it.delete() }
    }

    /** [ranked] doit déjà être trié (voir CostRanking.rank) — l'ordre des <wpt> suit cet ordre. */
    fun buildGpx(ranked: List<RankedStation>, fuel: FuelType): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"$CREATOR\"\n")
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")

        for (r in ranked) {
            val s = r.station
            val majCourte = formatMajCourte(s.majIso)
            val prixStr = String.format(Locale.FRANCE, "%.3f", s.prix)
            val prixEffectifStr = String.format(Locale.FRANCE, "%.3f", r.effectivePricePerLiter)
            val distanceStr = String.format(Locale.FRANCE, "%.1f", r.distanceKm)
            val name = "$prixStr€ ${fuel.label} · $majCourte"
            val desc = "${s.adresse}, ${s.codePostal} ${s.ville} — ${fuel.label} ${prixStr}€/L, mis à jour le ${s.majIso}" +
                " — ${distanceStr} km par la route, coût effectif détour inclus : ${prixEffectifStr}€/L"

            sb.append("  <wpt lat=\"${s.lat}\" lon=\"${s.lon}\">\n")
            sb.append("    <name>${escapeXml(name)}</name>\n")
            sb.append("    <desc>${escapeXml(desc)}</desc>\n")
            sb.append("    <cmt>Source: data.gouv.fr - Prix des carburants en France - Flux instantane v2</cmt>\n")
            sb.append("    <sym>Gas Station</sym>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("</gpx>\n")
        return sb.toString()
    }

    /**
     * Écrit le fichier GPX dans le cache privé de l'app (aucune permission de stockage) ; l'utilisateur
     * l'envoie ensuite à l'application de son choix avec [share] (cartes, gestionnaire de fichiers...).
     * Les précédents GPX de l'app sont supprimés : il n'y a jamais qu'un seul jeu de stations à la fois.
     */
    fun writeToShareDir(context: Context, ranked: List<RankedStation>, fuel: FuelType): File =
        writeGpxFile(context, "${fuel.apiField}_stations.gpx", buildGpx(ranked, fuel))

    /** Écrit [content] sous [fileName] dans le dossier de partage, en supprimant les GPX précédents de l'app. */
    fun writeGpxFile(context: Context, fileName: String, content: String): File {
        val dir = File(context.cacheDir, "gpx")
        if (!dir.exists()) dir.mkdirs()
        deleteOwnFiles(dir)
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /** Ouvre le sélecteur Android : l'utilisateur choisit l'application qui reçoit le GPX. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, file.name))
    }

    private fun formatMajCourte(iso: String): String {
        return try {
            val instant = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Europe/Paris"))
            instant.format(DateTimeFormatter.ofPattern("dd/MM HH'h'mm", Locale.FRANCE))
        } catch (e: Exception) {
            iso
        }
    }

    internal fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
