// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Lecture bornée des réponses réseau : un serveur ne doit pas pouvoir remplir la mémoire de l'app, et un
 * corps d'erreur ne doit jamais être affiché brut à l'utilisateur.
 */
object Http {
    const val MAX_RESPONSE_BYTES = 2_000_000

    /** Lit toute la réponse en UTF-8, ou lève une IOException si elle dépasse [maxBytes]. */
    fun readText(input: InputStream, maxBytes: Int = MAX_RESPONSE_BYTES): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("réponse trop volumineuse (> $maxBytes octets)")
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Début d'un corps d'erreur, sur une seule ligne : lit au plus 500 octets, jamais d'exception. */
    fun errorExcerpt(input: InputStream?, maxChars: Int = 120): String? {
        if (input == null) return null
        val raw = runCatching {
            val buffer = ByteArray(500)
            val n = input.use { it.read(buffer) }
            if (n > 0) String(buffer, 0, n, Charsets.UTF_8) else ""
        }.getOrDefault("")
        return excerpt(raw, maxChars)
    }

    fun excerpt(text: String, maxChars: Int = 120): String? {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return null
        return if (flat.length <= maxChars) flat else flat.take(maxChars) + "…"
    }
}
