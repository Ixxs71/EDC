// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Un point du jeu de données officiel, réduit aux seules coordonnées : la zone de vigilance ne
 * distingue jamais un point précis à l'utilisateur (voir VigilanceZone), inutile d'en garder plus. */
data class ControlPoint(val lat: Double, val lon: Double)

/**
 * Jeu de données ouvert du ministère de l'Intérieur (data.gouv.fr, licence LOV2, même permalien que
 * documenté dans CLAUDE.md), utilisé uniquement pour calculer une zone large (VigilanceZone) — jamais
 * pour afficher une position précise. Mise à jour officielle annuelle : cache local, rafraîchi
 * seulement au-delà de 180 jours.
 */
object SpeedControlRepository {
    private const val STABLE_URL = "https://www.data.gouv.fr/fr/datasets/r/17f7cfd9-a5fe-4b6a-9f5d-3625feaa396e"
    private val MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(180)

    private fun cacheFile(context: Context) = File(context.filesDir, "controle_vitesse.csv")

    /** Bloquant — à exécuter sur Dispatchers.IO. Ne lève jamais : renvoie la liste vide au pire. */
    fun getPoints(context: Context, forceRefresh: Boolean = false): List<ControlPoint> {
        val file = cacheFile(context)
        val stale = forceRefresh || !file.exists() ||
            (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MILLIS)
        if (stale) {
            try {
                download(file)
            } catch (e: Exception) {
                // Réseau indisponible : on garde le cache existant (même périmé) s'il y en a un.
            }
        }
        if (!file.exists()) return emptyList()
        return try {
            parse(file)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun download(destination: File) {
        val connection = URL(STABLE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "Edc/1.0 (Android)")
        try {
            val code = connection.responseCode
            if (code != 200) throw java.io.IOException("jeu de données indisponible (HTTP $code)")
            destination.outputStream().use { out ->
                connection.inputStream.use { it.copyTo(out) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(file: File): List<ControlPoint> =
        file.bufferedReader(Charsets.UTF_8).useLines { lines -> parseLines(lines) }

    /** Lignes du CSV officiel (séparateur ";", en-tête ignoré, colonnes latitude/longitude en position
     * 4 et 5) ; les lignes incomplètes ou sans coordonnées valides sont sautées. */
    internal fun parseLines(lines: Sequence<String>): List<ControlPoint> {
        val points = mutableListOf<ControlPoint>()
        var isHeader = true
        for (line in lines) {
            if (isHeader) {
                isHeader = false
                continue
            }
            if (line.isBlank()) continue
            val f = line.split(';')
            if (f.size < 6) continue
            val lat = f[4].trim().toDoubleOrNull() ?: continue
            val lon = f[5].trim().toDoubleOrNull() ?: continue
            points.add(ControlPoint(lat, lon))
        }
        return points
    }
}
