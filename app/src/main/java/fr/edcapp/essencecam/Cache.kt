// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CachedResult(
    val timestampMillis: Long,
    val ranked: List<RankedStation>,
    val distanceSource: String, // "route" ou "vol d'oiseau"
)

/** Dernier résultat obtenu avec succès, par carburant — affiché en secours si le réseau échoue. */
object Cache {
    private const val FILE = "gplfinder_cache"

    private fun key(fuel: FuelType) = "cache_${fuel.apiField}"

    fun save(context: Context, fuel: FuelType, ranked: List<RankedStation>, distanceSource: String) {
        val stationsArr = JSONArray()
        for (r in ranked) {
            val o = JSONObject()
            o.put("id", r.station.id)
            o.put("ville", r.station.ville)
            o.put("adresse", r.station.adresse)
            o.put("cp", r.station.codePostal)
            o.put("prix", r.station.prix)
            o.put("maj", r.station.majIso)
            o.put("lat", r.station.lat)
            o.put("lon", r.station.lon)
            o.put("distanceKm", r.distanceKm)
            o.put("prixEffectif", r.effectivePricePerLiter)
            stationsArr.put(o)
        }
        val root = JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        root.put("distanceSource", distanceSource)
        root.put("stations", stationsArr)

        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(key(fuel), root.toString())
            .apply()
    }

    fun load(context: Context, fuel: FuelType): CachedResult? {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key(fuel), null)
            ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("stations")
            val list = mutableListOf<RankedStation>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val station = GplStation(
                    id = o.optString("id", ""),
                    ville = o.getString("ville"),
                    adresse = o.getString("adresse"),
                    codePostal = o.getString("cp"),
                    prix = o.getDouble("prix"),
                    majIso = o.getString("maj"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                )
                list.add(RankedStation(station, o.getDouble("distanceKm"), o.getDouble("prixEffectif")))
            }
            CachedResult(root.getLong("timestamp"), list, root.optString("distanceSource", "?"))
        } catch (e: Exception) {
            null
        }
    }
}
