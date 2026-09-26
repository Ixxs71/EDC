// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class HttpTest {
    @Test
    fun `une reponse sous la limite est lue en entier`() {
        assertEquals("héllo", Http.readText(ByteArrayInputStream("héllo".toByteArray()), maxBytes = 100))
    }

    @Test
    fun `une reponse trop volumineuse est refusee`() {
        try {
            Http.readText(ByteArrayInputStream(ByteArray(1000)), maxBytes = 999)
            fail("IOException attendue")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("trop volumineuse"))
        }
    }

    @Test
    fun `la limite exacte est acceptee`() {
        assertEquals(1000, Http.readText(ByteArrayInputStream(ByteArray(1000) { 'a'.code.toByte() }), maxBytes = 1000).length)
    }

    @Test
    fun `un corps d'erreur est aplati et tronque`() {
        val body = "<html>\n  <body>" + "x".repeat(400) + "</body></html>"
        val excerpt = Http.errorExcerpt(ByteArrayInputStream(body.toByteArray()), maxChars = 60)!!
        assertEquals(61, excerpt.length)
        assertTrue(!excerpt.contains("\n") && excerpt.endsWith("…"))
    }

    @Test
    fun `un corps d'erreur vide ou absent donne null`() {
        assertNull(Http.errorExcerpt(null))
        assertNull(Http.errorExcerpt(ByteArrayInputStream(ByteArray(0))))
        assertNull(Http.excerpt("  \n\t "))
    }
}
