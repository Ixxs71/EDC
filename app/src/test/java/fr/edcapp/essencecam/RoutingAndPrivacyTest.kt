// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingAndPrivacyTest {

    @Test
    fun `la position est arrondie a 3 decimales pour le reseau`() {
        assertEquals(47.772, GeoPrivacy.roundForNetwork(47.771603), 1e-9)
        assertEquals(7.232, GeoPrivacy.roundForNetwork(7.23183), 1e-9)
        assertEquals(-1.554, GeoPrivacy.roundForNetwork(-1.55449), 1e-9)
    }

    @Test
    fun `haversine Paris - Lyon proche de 392 km`() {
        val km = GeoPrivacy.haversineKm(48.8566, 2.3522, 45.7640, 4.8357)
        assertEquals(392.0, km, 3.0)
    }

    @Test
    fun `la distance routiere estimee applique le facteur de detour`() {
        val straight = GeoPrivacy.haversineKm(48.0, 7.0, 48.1, 7.1)
        assertEquals(straight * 1.3, GeoPrivacy.estimatedRoadKm(48.0, 7.0, 48.1, 7.1), 1e-9)
        assertEquals(0.0, GeoPrivacy.estimatedRoadKm(48.0, 7.0, 48.0, 7.0), 1e-9)
    }

    @Test
    fun `seule une adresse https valide est acceptee comme serveur de routage`() {
        assertEquals("https://osrm.exemple.fr", RoutingApi.normalizeServer("  https://osrm.exemple.fr/  "))
        assertEquals("https://osrm.exemple.fr:5000/api", RoutingApi.normalizeServer("https://osrm.exemple.fr:5000/api/"))
        assertNull(RoutingApi.normalizeServer(null))
        assertNull(RoutingApi.normalizeServer(""))
        assertNull(RoutingApi.normalizeServer("http://osrm.exemple.fr"))
        assertNull(RoutingApi.normalizeServer("https://"))
        assertNull(RoutingApi.normalizeServer("https://osrm .exemple.fr"))
        assertNull(RoutingApi.normalizeServer("osrm.exemple.fr"))
    }

    @Test
    fun `l'url envoyee arrondit l'origine mais garde les stations exactes`() {
        val url = RoutingApi.buildTableUrl(
            "https://osrm.exemple.fr", 47.771603, 7.23183,
            listOf(47.7654321 to 7.2987654, 47.7 to 7.3),
        )
        assertEquals(
            "https://osrm.exemple.fr/table/v1/driving/7.232,47.772;7.2987654,47.7654321;7.3,47.7" +
                "?sources=0&annotations=distance",
            url,
        )
        assertTrue(!url.contains("47.771603") && !url.contains("7.23183"))
    }
}
