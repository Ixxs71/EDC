// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashcamFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `seuls les clips crees par l'app sont reconnus`() {
        val dir = folder.root
        val clip = folder.newFile("20260926_093253.mp4")
        val foreign = folder.newFile("vacances.mp4")
        val partial = folder.newFile("20260926_093253.mp4.tmp")
        val badDate = folder.newFile("2026926_093253.mp4")
        val subDirNamedLikeClip = folder.newFolder("20260101_000000.mp4")

        assertTrue(DashcamFiles.isClip(clip))
        assertFalse(DashcamFiles.isClip(foreign))
        assertFalse(DashcamFiles.isClip(partial))
        assertFalse(DashcamFiles.isClip(badDate))
        assertFalse(DashcamFiles.isClip(subDirNamedLikeClip))
        assertEquals(listOf(clip), DashcamFiles.clips(dir))
    }

    @Test
    fun `un dossier absent donne une liste vide`() {
        assertTrue(DashcamFiles.clips(folder.root.resolve("inexistant")).isEmpty())
    }
}
