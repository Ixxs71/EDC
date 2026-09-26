// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import java.time.Duration
import java.time.OffsetDateTime

/**
 * Fraîcheur d'une MAJ de prix. La fréquence de MAJ varie énormément selon le carburant
 * (quasi quotidienne pour Gazole/E10, plusieurs jours à plusieurs mois pour le GPLc) —
 * pas de seuil fixe pertinent. Le seuil d'alerte est donc la moyenne d'âge calculée sur
 * la liste de stations affichée à l'instant T, pas une constante.
 */
object Freshness {
    fun ageHours(majIso: String): Double? = try {
        Duration.between(OffsetDateTime.parse(majIso), OffsetDateTime.now()).toMinutes() / 60.0
    } catch (e: Exception) {
        null
    }

    fun averageAgeHours(ranked: List<RankedStation>): Double? {
        val ages = ranked.mapNotNull { ageHours(it.station.majIso) }
        return if (ages.isEmpty()) null else ages.average()
    }

    fun isStale(majIso: String, thresholdHours: Double): Boolean {
        val age = ageHours(majIso) ?: return false
        return age > thresholdHours
    }
}
