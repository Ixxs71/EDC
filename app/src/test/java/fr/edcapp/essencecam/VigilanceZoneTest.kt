// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilanceZoneTest {
    @Test
    fun `un point a moins de 4 km declenche l'entree en zone`() {
        assertTrue(VigilanceZone.isInZone(0.0))
        assertTrue(VigilanceZone.isInZone(3999.0))
        assertTrue(VigilanceZone.isInZone(VigilanceZone.RADIUS_METERS))
    }

    @Test
    fun `un point a plus de 4 km ne declenche pas l'entree`() {
        assertFalse(VigilanceZone.isInZone(4001.0))
        assertFalse(VigilanceZone.isInZone(10_000.0))
    }

    @Test
    fun `la sortie de zone exige une marge, pas juste repasser le seuil d'entree`() {
        // Entre 4 km et 6 km : toujours considéré "dans la zone" côté sortie (hystérésis).
        assertFalse(VigilanceZone.hasExitedZone(4500.0))
        assertFalse(VigilanceZone.hasExitedZone(VigilanceZone.EXIT_MARGIN_METERS))
        assertTrue(VigilanceZone.hasExitedZone(6001.0))
    }

    @Test
    fun `le rayon large est au moins aussi grand que les trois seuils du protocole de 2011`() {
        // Protocole d'accord Ministère de l'Intérieur / AFFTAC du 28 juillet 2011 : au moins 4 km
        // autoroute, 2 km route, 300 m agglomération (voir VigilanceZone.kt) — hors agglomération
        // repérée, un seul rayon commun ne doit jamais être plus précis qu'aucun des trois.
        assertTrue(VigilanceZone.RADIUS_METERS >= 4000.0)
        assertTrue(VigilanceZone.RADIUS_METERS >= 2000.0)
        assertTrue(VigilanceZone.RADIUS_METERS >= 300.0)
    }

    @Test
    fun `le rayon urbain est dans la fourchette 300 a 500 m du protocole, jamais en dessous`() {
        assertTrue(VigilanceZone.URBAN_RADIUS_METERS >= 300.0)
        assertTrue(VigilanceZone.URBAN_RADIUS_METERS <= 500.0)
    }

    @Test
    fun `le rayon urbain se comporte comme le rayon par defaut, juste plus petit`() {
        assertTrue(VigilanceZone.isInZone(400.0, VigilanceZone.URBAN_RADIUS_METERS))
        assertFalse(VigilanceZone.isInZone(600.0, VigilanceZone.URBAN_RADIUS_METERS))
        assertFalse(VigilanceZone.hasExitedZone(700.0, VigilanceZone.URBAN_EXIT_MARGIN_METERS))
        assertTrue(VigilanceZone.hasExitedZone(800.0, VigilanceZone.URBAN_EXIT_MARGIN_METERS))
    }
}
