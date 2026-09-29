// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Est-ce qu'une position donnée est probablement en agglomération ? Utilisé uniquement pour
 * resserrer le rayon de la zone de vigilance (voir VigilanceZone.URBAN_RADIUS_METERS) — jamais pour
 * afficher quoi que ce soit à l'utilisateur. Repose sur le géocodage inverse de la Géoplateforme IGN
 * (data.geopf.fr, licence etalab-2.0, gratuit, sans clé — vérifié en direct le 2026-09-29 : Paris
 * centre renvoie une adresse à 17 m, un point rural ne renvoie aucun résultat).
 *
 * "Probablement" : ce n'est PAS la définition légale exacte (unité urbaine INSEE), juste une
 * adresse connue à moins de DISTANCE_THRESHOLD_M — une approximation raisonnable, jamais utilisée
 * dans le sens dangereux (voir VigilanceService : toute incertitude retombe sur le rayon large).
 */
object AgglomerationCheck {
    private const val ENDPOINT = "https://data.geopf.fr/geocodage/reverse"
    private const val DISTANCE_THRESHOLD_M = 200.0
    private val PRECISE_TYPES = setOf("housenumber", "street")

    /** Bloquant — à exécuter sur Dispatchers.IO. Ne lève jamais : renvoie false (rayon large) au
     * moindre doute (réseau indisponible, réponse inattendue, aucune adresse proche). */
    fun isLikelyUrban(lat: Double, lon: Double): Boolean {
        val roundedLat = GeoPrivacy.roundForNetwork(lat)
        val roundedLon = GeoPrivacy.roundForNetwork(lon)
        val url = "$ENDPOINT?lon=" + URLEncoder.encode(roundedLon.toString(), "UTF-8") +
            "&lat=" + URLEncoder.encode(roundedLat.toString(), "UTF-8") + "&index=address&limit=1"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.setRequestProperty("User-Agent", "Edc/1.0 (Android)")
        return try {
            if (connection.responseCode != 200) return false
            val body = connection.inputStream.use { Http.readText(it) }
            val features = JSONObject(body).optJSONArray("features") ?: return false
            if (features.length() == 0) return false
            val props = features.getJSONObject(0).optJSONObject("properties") ?: return false
            val distance = props.optDouble("distance", Double.MAX_VALUE)
            val type = props.optString("type", "")
            distance <= DISTANCE_THRESHOLD_M && type in PRECISE_TYPES
        } catch (e: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
