// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

data class RankedStation(
    val station: GplStation,
    val distanceKm: Double, // distance routière réelle, aller simple
    val effectivePricePerLiter: Double,
)

/**
 * Classement par coût réel : une station moins chère au litre mais plus loin peut revenir
 * plus chère une fois le carburant du détour (aller-retour) compté.
 *
 * prix_effectif = prix × (1 + 2×distance_km×conso_L100km/100 / quantite_litres)
 */
object CostRanking {
    fun rank(
        stations: List<GplStation>,
        distancesKm: List<Double>,
        consommationL100km: Double,
        quantiteLitres: Double,
    ): List<RankedStation> {
        require(stations.size == distancesKm.size) { "stations et distances doivent avoir la même taille" }
        require(quantiteLitres > 0) { "la quantité doit être positive" }
        return stations.zip(distancesKm)
            // Prix ou distance absents/invalides (NaN) : la station est écartée plutôt que classée au hasard.
            .filter { (s, d) -> s.prix.isFinite() && s.prix > 0 && d.isFinite() && d >= 0 }
            .map { (s, d) ->
                val detourLitres = 2 * d * consommationL100km / 100.0
                val prixEffectif = s.prix * (1 + detourLitres / quantiteLitres)
                RankedStation(s, d, prixEffectif)
            }
            .sortedBy { it.effectivePricePerLiter }
    }
}
