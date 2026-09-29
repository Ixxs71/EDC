// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelTypeTest {
    @Test
    fun `six carburants avec des champs d'API distincts`() {
        assertEquals(6, FuelType.entries.size)
        assertEquals(6, FuelType.entries.map { it.apiField }.toSet().size)
    }

    @Test
    fun `un nom connu est retrouve`() {
        assertEquals(FuelType.GAZOLE, FuelType.fromName("GAZOLE"))
        assertEquals(FuelType.E85, FuelType.fromName("E85"))
    }

    @Test
    fun `un nom absent ou inconnu retombe sur le GPLc`() {
        assertEquals(FuelType.GPLC, FuelType.fromName(null))
        assertEquals(FuelType.GPLC, FuelType.fromName(""))
        assertEquals(FuelType.GPLC, FuelType.fromName("gazole"))
        assertEquals(FuelType.GPLC, FuelType.fromName("KEROSENE"))
    }

    @Test
    fun `les champs d'API sont ceux du jeu de donnees officiel`() {
        assertEquals(
            listOf("gazole", "sp95", "sp98", "e10", "e85", "gplc"),
            FuelType.entries.map { it.apiField },
        )
    }
}
