// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Test

class CostRankingTest {
    private fun station(id: String, prix: Double) =
        GplStation(id, "Ville", "1 rue", "68000", prix, "2026-09-26T00:00:00+00:00", 48.0, 7.0)

    @Test
    fun `le detour est amorti sur la quantite achetee`() {
        // 10 km aller, 10 L/100 km : 2 L de detour pour 40 L achetes => 0,90 x (1 + 2/40) = 0,945
        val ranked = CostRanking.rank(listOf(station("A", 0.90)), listOf(10.0), 10.0, 40.0)
        assertEquals(0.945, ranked[0].effectivePricePerLiter, 1e-9)
    }

    @Test
    fun `une station un peu plus chere mais proche passe devant une station lointaine`() {
        val far = station("loin", 0.90)
        val near = station("proche", 0.92)
        val ranked = CostRanking.rank(listOf(far, near), listOf(30.0, 0.0), 10.0, 40.0)
        assertEquals(listOf("proche", "loin"), ranked.map { it.station.id })
    }

    @Test
    fun `une station sans prix ou sans distance valide est ecartee`() {
        val ok = station("ok", 0.90)
        val noPrice = station("sans_prix", Double.NaN)
        val noRoute = station("sans_route", 0.80)
        val ranked = CostRanking.rank(listOf(ok, noPrice, noRoute), listOf(5.0, 1.0, Double.NaN), 10.0, 40.0)
        assertEquals(listOf("ok"), ranked.map { it.station.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `une quantite nulle est refusee`() {
        CostRanking.rank(listOf(station("A", 0.90)), listOf(1.0), 10.0, 0.0)
    }
}
