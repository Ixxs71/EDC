// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Position envoyée à un service externe : arrondie à 3 décimales (environ 110 m en latitude), ce qui
 * suffit pour chercher des stations dans un rayon de 5 à 50 km et évite de transmettre la position
 * exacte. Les distances estimées localement (haversine) utilisent, elles, la position exacte : rien
 * n'est envoyé.
 */
object GeoPrivacy {
    private const val DECIMALS = 3
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Une route est en moyenne plus longue que la ligne droite : facteur d'estimation courant. */
    const val DETOUR_FACTOR = 1.3

    fun roundForNetwork(value: Double): Double {
        val scale = 10.0.pow(DECIMALS)
        return (value * scale).roundToLong() / scale
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /** Distance routière estimée sans réseau : ligne droite × [DETOUR_FACTOR]. */
    fun estimatedRoadKm(originLat: Double, originLon: Double, lat: Double, lon: Double): Double =
        haversineKm(originLat, originLon, lat, lon) * DETOUR_FACTOR
}
