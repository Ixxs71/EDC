// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePlanTest {
    private val gb = 1_000_000_000L

    private fun clip(name: String, size: Long, prot: Boolean = false) = ClipInfo(name, size, prot)

    @Test
    fun `sous le plafond et avec assez d'espace libre, rien n'est supprime`() {
        val clips = listOf(clip("a", gb), clip("b", gb))
        assertEquals(emptyList<String>(), StoragePlan.toDelete(clips, capBytes = 8 * gb, freeBytes = 50 * gb))
    }

    @Test
    fun `au-dessus du plafond, les plus anciens partent en premier`() {
        val clips = listOf(clip("a", 3 * gb), clip("b", 3 * gb), clip("c", 3 * gb))
        assertEquals(listOf("a"), StoragePlan.toDelete(clips, capBytes = 8 * gb, freeBytes = 50 * gb))
        assertEquals(listOf("a", "b"), StoragePlan.toDelete(clips, capBytes = 4 * gb, freeBytes = 50 * gb))
    }

    @Test
    fun `un telephone presque plein force la suppression meme sous le plafond`() {
        val clips = listOf(clip("a", gb), clip("b", gb), clip("c", gb))
        // 0,5 Go libre, il en faut 1 : supprimer "a" libère 1 Go, donc 1,5 Go libre.
        assertEquals(listOf("a"), StoragePlan.toDelete(clips, capBytes = 8 * gb, freeBytes = gb / 2))
    }

    @Test
    fun `un clip protege n'est jamais supprime et ne compte pas dans le plafond`() {
        val clips = listOf(clip("a", 5 * gb, prot = true), clip("b", 3 * gb), clip("c", 3 * gb))
        // Non protégés : 6 Go <= 8 Go, le clip protégé de 5 Go n'entre pas dans le calcul.
        assertEquals(emptyList<String>(), StoragePlan.toDelete(clips, capBytes = 8 * gb, freeBytes = 50 * gb))
        // Plafond 4 Go : "b" part, "a" reste malgré son anciennete.
        assertEquals(listOf("b"), StoragePlan.toDelete(clips, capBytes = 4 * gb, freeBytes = 50 * gb))
    }

    @Test
    fun `si tout est protege, rien n'est supprime meme sans espace`() {
        val clips = listOf(clip("a", gb, prot = true), clip("b", gb, prot = true))
        assertEquals(emptyList<String>(), StoragePlan.toDelete(clips, capBytes = gb, freeBytes = 0))
    }

    @Test
    fun `l'enregistrement demande au moins le minimum libre`() {
        assertTrue(StoragePlan.canRecord(StoragePlan.MIN_FREE_BYTES))
        assertFalse(StoragePlan.canRecord(StoragePlan.MIN_FREE_BYTES - 1))
    }
}
