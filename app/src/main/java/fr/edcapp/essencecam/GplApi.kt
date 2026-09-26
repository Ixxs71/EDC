// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Une station distribuant le carburant demandé, avec le prix et la date de mise à jour renvoyés par l'API. */
data class GplStation(
    val id: String, // identifiant officiel prix-carburants.gouv.fr — sert à joindre EnseigneRepository
    val ville: String,
    val adresse: String,
    val codePostal: String,
    val prix: Double,
    val majIso: String, // horodatage UTC ISO-8601, ex. 2026-08-25T00:01:00+00:00
    val lat: Double,
    val lon: Double,
)

/**
 * Requête l'API "Prix des carburants en France - Flux instantane v2" du ministère
 * (data.economie.gouv.fr, Opendatasoft Explore v2.1) via un filtre géographique par rayon.
 * Champs et syntaxe de requête vérifiés en direct le 2026-08-25.
 */
object GplApi {
    private const val BASE_URL =
        "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
            "prix-des-carburants-en-france-flux-instantane-v2/records"

    fun fetchNearbyStations(
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        limit: Int,
        fuel: FuelType,
    ): List<GplStation> {
        // Position arrondie (environ 110 m) : suffisant pour un rayon de 5 à 50 km, sans envoyer la position exacte.
        val point = "POINT(${GeoPrivacy.roundForNetwork(lon)} ${GeoPrivacy.roundForNetwork(lat)})"
        val prixField = "${fuel.apiField}_prix"
        val majField = "${fuel.apiField}_maj"
        val where = "distance(geom, geom'$point', ${radiusMeters}m) and $prixField is not null"
        val orderBy = "distance(geom, geom'$point') asc"

        val url = BASE_URL + "?" +
            "where=" + URLEncoder.encode(where, "UTF-8") +
            "&order_by=" + URLEncoder.encode(orderBy, "UTF-8") +
            "&limit=" + limit

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "EDC/1.0 (Android)")

        try {
            val code = connection.responseCode
            if (code != 200) {
                val errorBody = Http.errorExcerpt(connection.errorStream)
                throw IOException("data.gouv.fr indisponible (HTTP $code)${if (errorBody != null) " : $errorBody" else ""}")
            }
            val body = connection.inputStream.use { Http.readText(it) }
            val json = JSONObject(body)
            val results = json.getJSONArray("results")

            val stations = mutableListOf<GplStation>()
            for (i in 0 until results.length()) {
                val rec = results.getJSONObject(i)
                val geom = rec.optJSONObject("geom") ?: continue
                stations.add(
                    GplStation(
                        id = rec.optLong("id", -1L).toString(),
                        ville = rec.optString("ville", "?"),
                        adresse = rec.optString("adresse", ""),
                        codePostal = rec.optString("cp", ""),
                        prix = rec.optDouble(prixField, Double.NaN),
                        majIso = rec.optString(majField, ""),
                        lat = geom.optDouble("lat"),
                        lon = geom.optDouble("lon"),
                    )
                )
            }
            return stations
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("data.gouv.fr indisponible : ${e.message ?: e.toString()}")
        } finally {
            connection.disconnect()
        }
    }
}
