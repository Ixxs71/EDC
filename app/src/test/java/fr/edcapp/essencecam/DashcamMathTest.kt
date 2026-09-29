// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashcamMathTest {
    @Test
    fun `8 Go a 17 Mbit par seconde tiennent 1 h 2 min`() {
        // 8 Go = 64 Gbit ; 64e9 / 17e6 = 3764 s
        assertEquals(3764L, DashcamMath.retentionSeconds(8, 17_000_000))
        assertEquals("1 h 2 min", DashcamMath.formatDuration(3764))
    }

    @Test
    fun `un plafond ou un debit invalide n'a pas d'estimation`() {
        assertNull(DashcamMath.retentionSeconds(0, 17_000_000))
        assertNull(DashcamMath.retentionSeconds(-3, 17_000_000))
        assertNull(DashcamMath.retentionSeconds(8, 0))
    }

    @Test
    fun `moins d'une heure s'ecrit en minutes seulement`() {
        assertEquals("30 min", DashcamMath.formatDuration(1800))
        assertEquals("0 min", DashcamMath.formatDuration(59))
    }

    @Test
    fun `une heure pleine garde les minutes`() {
        assertEquals("1 h 0 min", DashcamMath.formatDuration(3600))
        assertEquals("2 h 30 min", DashcamMath.formatDuration(9000))
    }

    @Test
    fun `doubler le plafond double la duree`() {
        val one = DashcamMath.retentionSeconds(4, 10_000_000)!!
        val two = DashcamMath.retentionSeconds(8, 10_000_000)!!
        assertEquals(one * 2, two)
    }
}
