// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecimalDigitsTest {
    private val oneDigit = DecimalDigitsInputFilter(1)

    @Test
    fun `une decimale maximum apres le point ou la virgule`() {
        assertTrue(oneDigit.isAllowed("12", 2, 2, ".5"))
        assertTrue(oneDigit.isAllowed("12", 2, 2, ",5"))
        assertFalse(oneDigit.isAllowed("12.3", 4, 4, "4"))
    }

    @Test
    fun `les chiffres de la partie entiere restent libres`() {
        assertTrue(oneDigit.isAllowed("12345", 5, 5, "6"))
        assertTrue(oneDigit.isAllowed("", 0, 0, "7"))
    }

    @Test
    fun `les lettres et un second separateur sont refuses`() {
        assertFalse(oneDigit.isAllowed("1", 1, 1, "a"))
        assertFalse(oneDigit.isAllowed("1.2", 3, 3, "."))
        assertFalse(oneDigit.isAllowed("1", 1, 1, "-"))
    }

    @Test
    fun `effacer jusqu'a vider le champ est permis`() {
        assertTrue(oneDigit.isAllowed("5", 0, 1, ""))
    }

    @Test
    fun `sans decimale autorisee le separateur seul passe mais pas la decimale`() {
        val none = DecimalDigitsInputFilter(0)
        assertTrue(none.isAllowed("12", 2, 2, "."))
        assertFalse(none.isAllowed("12.", 3, 3, "5"))
    }

    @Test
    fun `remplacer une selection au milieu du texte`() {
        assertTrue(oneDigit.isAllowed("12.3", 3, 4, "9"))
        assertFalse(oneDigit.isAllowed("12.3", 1, 1, ".5"))
    }
}
