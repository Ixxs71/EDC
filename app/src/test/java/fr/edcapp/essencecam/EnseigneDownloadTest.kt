// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException

class EnseigneDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val valid = "id_station_officiel,nom_normalise\n1000001,Carrefour\n1000002,Total\n"

    @Test
    fun `un fichier valide remplace l'ancien`() {
        val dest = folder.newFile("enseignes.csv").also { it.writeText("ancien") }
        EnseigneRepository.writeAtomically(dest, ByteArrayInputStream(valid.toByteArray()), 1_000)
        assertEquals(valid, dest.readText())
        assertFalse(java.io.File(dest.parentFile, "enseignes.csv.tmp").exists())
    }

    @Test
    fun `un fichier trop gros laisse l'ancien intact`() {
        val dest = folder.newFile("enseignes.csv").also { it.writeText("ancien") }
        try {
            EnseigneRepository.writeAtomically(dest, ByteArrayInputStream(valid.toByteArray()), 10)
            fail("IOException attendue")
        } catch (e: IOException) {
            // attendu
        }
        assertEquals("ancien", dest.readText())
        assertFalse(java.io.File(dest.parentFile, "enseignes.csv.tmp").exists())
    }

    @Test
    fun `un fichier au mauvais format laisse l'ancien intact`() {
        val dest = folder.newFile("enseignes.csv").also { it.writeText("ancien") }
        try {
            EnseigneRepository.writeAtomically(dest, ByteArrayInputStream("<html>erreur</html>".toByteArray()), 1_000)
            fail("IOException attendue")
        } catch (e: IOException) {
            // attendu
        }
        assertEquals("ancien", dest.readText())
    }

    @Test
    fun `une coupure en cours de lecture ne laisse aucun fichier partiel`() {
        val dest = folder.root.resolve("enseignes.csv")
        val broken = object : java.io.InputStream() {
            var sent = false
            override fun read(): Int = throw IOException("connexion coupée")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!sent) {
                    sent = true
                    val part = "id_station_officiel,nom".toByteArray()
                    System.arraycopy(part, 0, b, off, part.size)
                    return part.size
                }
                throw IOException("connexion coupée")
            }
        }
        try {
            EnseigneRepository.writeAtomically(dest, broken, 1_000)
            fail("IOException attendue")
        } catch (e: IOException) {
            // attendu
        }
        assertFalse(dest.exists())
        assertTrue(folder.root.list()!!.isEmpty())
    }

    @Test
    fun `le BOM est toleré en tete de fichier`() {
        val dest = folder.root.resolve("enseignes.csv")
        EnseigneRepository.writeAtomically(dest, ByteArrayInputStream(("﻿" + valid).toByteArray()), 1_000)
        assertTrue(dest.exists())
    }
}
