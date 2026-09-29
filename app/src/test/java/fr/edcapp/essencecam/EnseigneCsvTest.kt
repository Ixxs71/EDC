// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Test

class EnseigneCsvTest {
    private fun parse(line: String) = EnseigneRepository.parseCsvLine(line)

    @Test
    fun `des champs simples separes par des virgules`() {
        assertEquals(listOf("a", "b", "c"), parse("a,b,c"))
    }

    @Test
    fun `une virgule entre guillemets ne separe pas`() {
        assertEquals(listOf("Total, Access", "x"), parse("\"Total, Access\",x"))
    }

    @Test
    fun `des guillemets doubles donnent un guillemet`() {
        assertEquals(listOf("Il dit \"oui\"", "1"), parse("\"Il dit \"\"oui\"\"\",1"))
    }

    @Test
    fun `les champs vides sont conserves`() {
        assertEquals(listOf("", "", ""), parse(",,"))
        assertEquals(listOf("a", ""), parse("a,"))
        assertEquals(listOf(""), parse(""))
    }

    @Test
    fun `un identifiant et un nom d'enseigne`() {
        assertEquals(listOf("68000001", "Carrefour"), parse("68000001,Carrefour"))
    }
}
