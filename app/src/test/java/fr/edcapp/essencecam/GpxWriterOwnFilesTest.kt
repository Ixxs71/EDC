// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GpxWriterOwnFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun gpx(name: String, creator: String) =
        folder.newFile(name).also {
            it.writeText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"$creator\">\n</gpx>\n")
        }

    @Test
    fun `deleteOwnFiles ne supprime que les fichiers de l'app`() {
        folder.newFile("gplc_stations.gpx").writeText("contenu quelconque")
        gpx("autre_export_de_l_app.gpx", GpxWriter.CREATOR)
        gpx("mes_randonnees.gpx", "Randonnee Pro")
        gpx("gplc_stations_de_quelquun_d_autre.gpx", "OsmAnd")
        folder.newFile("notes.txt").writeText("à garder")
        folder.newFolder("sous_dossier")

        GpxWriter.deleteOwnFiles(folder.root)

        val remaining = folder.root.list()!!.sorted()
        assertEquals(
            listOf("gplc_stations_de_quelquun_d_autre.gpx", "mes_randonnees.gpx", "notes.txt", "sous_dossier"),
            remaining,
        )
    }

    @Test
    fun `chaque carburant a un nom de fichier reconnu`() {
        for (fuel in FuelType.entries) folder.newFile("${fuel.apiField}_stations.gpx")
        GpxWriter.deleteOwnFiles(folder.root)
        assertEquals(emptyList<String>(), folder.root.list()!!.toList())
    }

    @Test
    fun `un dossier absent ne provoque pas d'erreur`() {
        GpxWriter.deleteOwnFiles(folder.root.resolve("inexistant"))
    }
}
