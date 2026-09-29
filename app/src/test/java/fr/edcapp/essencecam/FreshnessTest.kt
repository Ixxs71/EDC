// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class FreshnessTest {
    private fun isoHoursAgo(hours: Long) = OffsetDateTime.now().minusHours(hours).toString()

    private fun ranked(majIso: String) =
        RankedStation(GplStation("1", "Ville", "1 rue", "68000", 1.0, majIso, 48.0, 7.0), 1.0, 1.0)

    @Test
    fun `l'age d'une mise a jour est calcule en heures`() {
        val age = Freshness.ageHours(isoHoursAgo(5))
        assertNotNull(age)
        assertEquals(5.0, age!!, 0.1)
    }

    @Test
    fun `une date illisible n'a pas d'age et n'est jamais perimee`() {
        assertNull(Freshness.ageHours(""))
        assertNull(Freshness.ageHours("hier"))
        assertFalse(Freshness.isStale("hier", 1.0))
    }

    @Test
    fun `le seuil separe les mises a jour recentes des anciennes`() {
        assertTrue(Freshness.isStale(isoHoursAgo(6), 5.0))
        assertFalse(Freshness.isStale(isoHoursAgo(4), 5.0))
    }

    @Test
    fun `la moyenne d'age ignore les dates illisibles`() {
        val list = listOf(ranked(isoHoursAgo(2)), ranked(isoHoursAgo(4)), ranked("n/a"))
        assertEquals(3.0, Freshness.averageAgeHours(list)!!, 0.1)
    }

    @Test
    fun `sans aucune date lisible la moyenne est absente`() {
        assertNull(Freshness.averageAgeHours(emptyList()))
        assertNull(Freshness.averageAgeHours(listOf(ranked(""))))
    }
}
