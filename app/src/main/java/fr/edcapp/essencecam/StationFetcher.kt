// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

/** Origine d'une distance, affichée à l'utilisateur quand elle n'est pas une distance routière. */
object DistanceSource {
    const val ROUTE = "route"
    const val ESTIMATE = "estimation (vol d'oiseau × 1,3)"
}

/**
 * Enchaîne data.gouv.fr -> distances -> classement par coût réel. Les distances viennent d'un serveur
 * OSRM si l'utilisateur en a indiqué un (RoutingApi), sinon elles sont estimées sur le téléphone.
 */
object StationFetcher {
    data class Result(val ranked: List<RankedStation>, val distanceSource: String)

    /** Appel réseau bloquant — à exécuter sur Dispatchers.IO. [routingServer] : adresse https d'un serveur OSRM, ou vide. */
    fun fetch(
        lat: Double,
        lon: Double,
        radiusKm: Int,
        fuel: FuelType,
        conso: Double,
        quantite: Double,
        routingServer: String?,
    ): Result {
        val stations = GplApi.fetchNearbyStations(lat, lon, radiusMeters = radiusKm * 1000, limit = 30, fuel = fuel)
        val points = stations.map { it.lat to it.lon }

        val server = RoutingApi.normalizeServer(routingServer)
        val estimate = { points.map { (sLat, sLon) -> GeoPrivacy.estimatedRoadKm(lat, lon, sLat, sLon) } }
        val (distancesKm, source) = if (server == null) {
            estimate() to DistanceSource.ESTIMATE
        } else {
            try {
                // Une destination sans itinéraire (NaN) retombe sur l'estimation plutôt que de disparaître.
                val estimated = estimate()
                val routed = RoutingApi.fetchDrivingDistancesMeters(server, lat, lon, points).map { it / 1000.0 }
                routed.mapIndexed { i, km -> if (km.isFinite()) km else estimated[i] } to DistanceSource.ROUTE
            } catch (e: Exception) {
                estimate() to "${DistanceSource.ESTIMATE} — ${e.message}"
            }
        }

        val ranked = CostRanking.rank(stations, distancesKm, conso, quantite)
        return Result(ranked, source)
    }
}
