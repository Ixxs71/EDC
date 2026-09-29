// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedControlParseTest {
    private val header = "Numéro;Type;Date de mise en service;VMA ;Latitude; Longitude"

    private fun parse(vararg lines: String) = SpeedControlRepository.parseLines(sequenceOf(header, *lines))

    @Test
    fun `une ligne valide ne garde que les coordonnees`() {
        val points = parse("123;ETF;2020-01-01;80;48.5; 7.25")
        assertEquals(listOf(ControlPoint(48.5, 7.25)), points)
    }

    @Test
    fun `les lignes vides, courtes ou sans coordonnees sont sautees`() {
        val points = parse("", "1;ETF;d;50;48.0", "2;ETF;d;50;abc;7.0", "3;ETF;d;50;48.0;xyz", "4;ETU;d;30;47.5;7.5")
        assertEquals(listOf(ControlPoint(47.5, 7.5)), points)
    }

    @Test
    fun `l'en-tete est toujours ignore`() {
        assertTrue(SpeedControlRepository.parseLines(sequenceOf(header)).isEmpty())
        assertTrue(SpeedControlRepository.parseLines(emptySequence()).isEmpty())
    }
}
