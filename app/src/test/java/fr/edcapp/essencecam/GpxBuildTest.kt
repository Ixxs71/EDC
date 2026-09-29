// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxBuildTest {
    private fun station(id: String, prix: Double, maj: String, adresse: String = "1 rue Haute") =
        GplStation(id, "Colmar", adresse, "68000", prix, maj, 48.0, 7.0)

    private fun ranked(s: GplStation, km: Double = 3.24, effectif: Double = 1.5) = RankedStation(s, km, effectif)

    private fun count(text: String, part: String) = text.split(part).size - 1

    @Test
    fun `l'en-tete porte la marque de l'app et une balise par station dans l'ordre donne`() {
        val gpx = GpxWriter.buildGpx(
            listOf(ranked(station("A", 1.459, "2026-09-26T08:00:00+00:00")), ranked(station("B", 1.5, "2026-09-26T08:00:00+00:00"))),
            FuelType.GPLC,
        )
        assertTrue(gpx.contains("creator=\"${GpxWriter.CREATOR}\""))
        assertEquals(2, count(gpx, "<wpt "))
        assertTrue(gpx.indexOf("1,459") < gpx.indexOf("1,500"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `le prix et la distance utilisent la virgule francaise`() {
        val gpx = GpxWriter.buildGpx(listOf(ranked(station("A", 1.459, "2026-09-26T08:00:00+00:00"))), FuelType.GPLC)
        assertTrue(gpx.contains("1,459€ GPLc"))
        assertTrue(gpx.contains("3,2 km"))
        assertTrue(gpx.contains("1,500€/L"))
    }

    @Test
    fun `la date de mise a jour est affichee a l'heure de Paris, ete et hiver`() {
        val summer = GpxWriter.buildGpx(listOf(ranked(station("A", 1.4, "2026-09-26T08:00:00+00:00"))), FuelType.GAZOLE)
        val winter = GpxWriter.buildGpx(listOf(ranked(station("A", 1.4, "2026-01-15T08:00:00+00:00"))), FuelType.GAZOLE)
        assertTrue(summer.contains("26/09 10h00"))
        assertTrue(winter.contains("15/01 09h00"))
    }

    @Test
    fun `une date illisible est reprise telle quelle`() {
        val gpx = GpxWriter.buildGpx(listOf(ranked(station("A", 1.4, "n/a"))), FuelType.E10)
        assertTrue(gpx.contains("· n/a"))
    }

    @Test
    fun `les caracteres XML de l'adresse sont echappes`() {
        val gpx = GpxWriter.buildGpx(
            listOf(ranked(station("A", 1.4, "2026-09-26T08:00:00+00:00", adresse = "Zone A&B <nord>"))),
            FuelType.SP95,
        )
        assertTrue(gpx.contains("Zone A&amp;B &lt;nord&gt;"))
        assertTrue(!gpx.contains("Zone A&B"))
    }

    @Test
    fun `une liste vide donne un GPX valide sans balise`() {
        val gpx = GpxWriter.buildGpx(emptyList(), FuelType.GPLC)
        assertEquals(0, count(gpx, "<wpt "))
        assertTrue(gpx.contains("</gpx>"))
    }

    @Test
    fun `escapeXml traite les trois caracteres reserves`() {
        assertEquals("a&amp;b &lt;c&gt;", GpxWriter.escapeXml("a&b <c>"))
        assertEquals("rien", GpxWriter.escapeXml("rien"))
    }
}
