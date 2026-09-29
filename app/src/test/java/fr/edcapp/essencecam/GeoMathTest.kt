// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun `deux points identiques sont a distance nulle`() {
        assertEquals(0.0, GeoPrivacy.haversineKm(48.5, 7.3, 48.5, 7.3), 1e-9)
    }

    @Test
    fun `la distance ne depend pas du sens`() {
        val ab = GeoPrivacy.haversineKm(48.0, 7.0, 45.75, 4.85)
        val ba = GeoPrivacy.haversineKm(45.75, 4.85, 48.0, 7.0)
        assertEquals(ab, ba, 1e-9)
    }

    @Test
    fun `un degre de latitude vaut environ 111 km`() {
        assertEquals(111.2, GeoPrivacy.haversineKm(48.0, 7.0, 49.0, 7.0), 0.1)
    }

    @Test
    fun `l'arrondi reseau gere les valeurs negatives`() {
        assertEquals(-1.235, GeoPrivacy.roundForNetwork(-1.23456), 1e-9)
        assertEquals(2.345, GeoPrivacy.roundForNetwork(2.3454), 1e-9)
        assertEquals(0.0, GeoPrivacy.roundForNetwork(0.0), 1e-12)
    }

    @Test
    fun `l'arrondi reste a moins de 60 metres de la position d'origine`() {
        val lat = 48.123456
        val lon = 7.654321
        val d = GeoPrivacy.haversineKm(lat, lon, GeoPrivacy.roundForNetwork(lat), GeoPrivacy.roundForNetwork(lon))
        assertEquals(0.0, d, 0.06)
    }
}
