// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LocationPlanTest {
    private val fused = LocationManager.FUSED_PROVIDER
    private val network = LocationManager.NETWORK_PROVIDER
    private val gps = LocationManager.GPS_PROVIDER

    @Test
    fun `position approximative sur Android 12 et plus, fused puis reseau, jamais le GPS`() {
        assertEquals(listOf(fused, network), LocationPlan.providers(sdkInt = 34, hasFine = false))
    }

    @Test
    fun `position approximative avant Android 12, reseau seulement`() {
        assertEquals(listOf(network), LocationPlan.providers(sdkInt = 28, hasFine = false))
    }

    @Test
    fun `avec la position precise le GPS est ajoute en dernier recours`() {
        assertEquals(listOf(fused, network, gps), LocationPlan.providers(sdkInt = 34, hasFine = true))
        assertEquals(listOf(network, gps), LocationPlan.providers(sdkInt = 27, hasFine = true))
    }

    @Test
    fun `le GPS a un delai plus long que les autres fournisseurs`() {
        assertTrue(LocationPlan.timeoutMs(gps) > LocationPlan.timeoutMs(network))
        assertEquals(LocationPlan.timeoutMs(fused), LocationPlan.timeoutMs(network))
    }

    @Test
    fun `une ancienne position n'est jamais acceptee`() {
        assertTrue(LocationPlan.isFresh(TimeUnit.MINUTES.toNanos(2)))
        assertTrue(LocationPlan.isFresh(TimeUnit.MINUTES.toNanos(LocationPlan.MAX_LAST_KNOWN_AGE_MINUTES)))
        assertFalse(LocationPlan.isFresh(TimeUnit.MINUTES.toNanos(LocationPlan.MAX_LAST_KNOWN_AGE_MINUTES + 1)))
        assertFalse(LocationPlan.isFresh(TimeUnit.HOURS.toNanos(6)))
        assertFalse(LocationPlan.isFresh(-1))
    }
}
