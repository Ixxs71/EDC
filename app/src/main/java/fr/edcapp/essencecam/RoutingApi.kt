// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Distances routières réelles via un serveur OSRM (API "table"), à l'adresse que l'utilisateur
 * indique — par exemple le sien. Aucun serveur n'est imposé par défaut dans l'édition publique :
 * l'instance de démonstration publique du projet OSRM est limitée à un usage
 * raisonnable non commercial, sans déploiement en production et sans garantie de disponibilité, ce qui
 * ne convient pas à une app diffusée à plusieurs utilisateurs. Sans serveur, les distances sont
 * estimées sur le téléphone (voir GeoPrivacy.estimatedRoadKm).
 *
 * La position de l'utilisateur est arrondie avant l'envoi (GeoPrivacy.roundForNetwork).
 */
object RoutingApi {

    /** Adresse de serveur acceptée : https uniquement, sans espace, terminée sans "/". Sinon null. */
    fun normalizeServer(raw: String?): String? {
        val url = raw?.trim()?.trimEnd('/') ?: return null
        if (url.isEmpty() || !url.startsWith("https://") || url.length <= "https://".length) return null
        if (url.any { it.isWhitespace() }) return null
        val host = runCatching { URL(url).host }.getOrNull()
        return if (host.isNullOrEmpty()) null else url
    }

    fun buildTableUrl(server: String, originLat: Double, originLon: Double, destinations: List<Pair<Double, Double>>): String {
        val coords = buildString {
            append("${GeoPrivacy.roundForNetwork(originLon)},${GeoPrivacy.roundForNetwork(originLat)}")
            // Les stations sont des lieux publics : coordonnées exactes. Seule la position de
            // l'utilisateur est arrondie.
            for ((lat, lon) in destinations) append(";$lon,$lat")
        }
        return "$server/table/v1/driving/$coords?sources=0&annotations=distance"
    }

    /**
     * Distance routière (en mètres) depuis (originLat, originLon) vers chaque point de
     * [destinations] (lat, lon), dans le même ordre. Un seul appel HTTP pour tous les points.
     * [server] doit venir de [normalizeServer].
     */
    fun fetchDrivingDistancesMeters(
        server: String,
        originLat: Double,
        originLon: Double,
        destinations: List<Pair<Double, Double>>,
    ): List<Double> {
        if (destinations.isEmpty()) return emptyList()

        val connection = URL(buildTableUrl(server, originLat, originLon, destinations)).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "EDC/1.0 (Android)")

        try {
            val code = connection.responseCode
            if (code != 200) {
                val errorBody = Http.errorExcerpt(connection.errorStream)
                throw IOException("serveur de routage indisponible (HTTP $code)${if (errorBody != null) " : $errorBody" else ""}")
            }
            val body = connection.inputStream.use { Http.readText(it) }
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                throw IOException("serveur de routage indisponible : ${json.optString("code", "erreur inconnue")}")
            }

            // row[0] = origine->origine (0, ignoré), row[1..N] = origine->destinations, même ordre.
            val row = json.getJSONArray("distances").getJSONArray(0)
            return (1 until row.length()).map { i -> row.optDouble(i, Double.NaN) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("serveur de routage indisponible : ${e.message ?: e.toString()}")
        } finally {
            connection.disconnect()
        }
    }
}
