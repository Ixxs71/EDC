// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CostRankingEdgeTest {
    private fun station(id: String, prix: Double) =
        GplStation(id, "Ville", "1 rue", "68000", prix, "2026-09-26T00:00:00+00:00", 48.0, 7.0)

    @Test
    fun `sans detour le prix effectif est le prix affiche`() {
        val ranked = CostRanking.rank(listOf(station("A", 1.5)), listOf(0.0), 8.0, 30.0)
        assertEquals(1.5, ranked[0].effectivePricePerLiter, 1e-12)
    }

    @Test
    fun `une consommation nulle annule le cout du detour`() {
        val ranked = CostRanking.rank(listOf(station("A", 1.5)), listOf(100.0), 0.0, 30.0)
        assertEquals(1.5, ranked[0].effectivePricePerLiter, 1e-12)
    }

    @Test
    fun `des prix effectifs egaux gardent l'ordre d'entree`() {
        val ranked = CostRanking.rank(listOf(station("A", 1.5), station("B", 1.5)), listOf(0.0, 0.0), 8.0, 30.0)
        assertEquals(listOf("A", "B"), ranked.map { it.station.id })
    }

    @Test
    fun `un prix nul ou negatif et une distance negative sont ecartes`() {
        val ranked = CostRanking.rank(
            listOf(station("ok", 1.5), station("zero", 0.0), station("neg", -1.0), station("dist", 1.4)),
            listOf(1.0, 1.0, 1.0, -2.0),
            8.0, 30.0,
        )
        assertEquals(listOf("ok"), ranked.map { it.station.id })
    }

    @Test
    fun `une liste vide donne un classement vide`() {
        assertTrue(CostRanking.rank(emptyList(), emptyList(), 8.0, 30.0).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `des tailles differentes sont refusees`() {
        CostRanking.rank(listOf(station("A", 1.5)), listOf(1.0, 2.0), 8.0, 30.0)
    }

    @Test
    fun `plus la quantite est grande, plus le detour pese peu`() {
        val petit = CostRanking.rank(listOf(station("A", 1.5)), listOf(10.0), 8.0, 10.0)[0].effectivePricePerLiter
        val grand = CostRanking.rank(listOf(station("A", 1.5)), listOf(10.0), 8.0, 60.0)[0].effectivePricePerLiter
        assertTrue(grand < petit)
    }
}
