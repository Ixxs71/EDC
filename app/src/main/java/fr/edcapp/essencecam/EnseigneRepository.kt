// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Nom d'enseigne (Total, Carrefour, Intermarché...) par identifiant officiel de station.
 * Absent de l'API prix-carburants.gouv.fr — vient d'un référentiel tiers croisé avec
 * OpenStreetMap (organisation Chiffrex sur data.gouv.fr, licence odc-odbl, MAJ hebdomadaire).
 * Pas une source officielle : peut manquer ou être approximatif pour certaines stations.
 */
object EnseigneRepository {
    // Permalien stable data.gouv.fr : redirige toujours vers la dernière version du fichier,
    // même quand le référentiel est mis à jour (vérifié en direct le 2026-08-30).
    private const val STABLE_URL = "https://www.data.gouv.fr/fr/datasets/r/0207ded0-2d19-47af-b1a6-62915ec1b721"
    private val MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(30)

    private fun cacheFile(context: Context) = File(context.filesDir, "enseignes.csv")

    /** Bloquant — à exécuter sur Dispatchers.IO. Ne lève jamais : renvoie une map vide au pire. */
    fun getMap(context: Context): Map<String, String> {
        val file = cacheFile(context)
        val stale = !file.exists() || (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MILLIS)
        if (stale) {
            try {
                download(file)
            } catch (e: Exception) {
                // Réseau indisponible : on retombe sur le cache existant (même périmé) s'il y en a un.
            }
        }
        if (!file.exists()) return emptyMap()
        return try {
            parse(file)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024
    private const val EXPECTED_HEADER = "id_station_officiel"

    /**
     * Écrit [input] dans [destination] sans jamais laisser un fichier partiel : fichier temporaire voisin,
     * taille plafonnée, en-tête CSV vérifié, puis renommage. Une coupure réseau ou une réponse inattendue
     * laisse l'ancien fichier intact (avant, un téléchargement tronqué restait "valide" 30 jours).
     */
    internal fun writeAtomically(destination: File, input: java.io.InputStream, maxBytes: Long) {
        val tmp = File(destination.parentFile, destination.name + ".tmp")
        try {
            var total = 0L
            tmp.outputStream().use { out ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw java.io.IOException("fichier trop volumineux (> $maxBytes octets)")
                    out.write(buffer, 0, n)
                }
            }
            val header = tmp.bufferedReader(Charsets.UTF_8).use { it.readLine() }?.removePrefix("\uFEFF")
            if (header == null || !header.startsWith(EXPECTED_HEADER)) throw java.io.IOException("format de fichier inattendu")
            try {
                java.nio.file.Files.move(
                    tmp.toPath(), destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(tmp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun download(destination: File) {
        val connection = URL(STABLE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "EDC/1.0 (Android)")
        try {
            val code = connection.responseCode
            if (code != 200) throw java.io.IOException("referentiel enseignes indisponible (HTTP $code)")
            connection.inputStream.use { writeAtomically(destination, it, MAX_DOWNLOAD_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(file: File): Map<String, String> {
        val map = HashMap<String, String>()
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            var isHeader = true
            for (line in lines) {
                if (isHeader) {
                    isHeader = false
                    continue
                }
                if (line.isBlank()) continue
                val fields = parseCsvLine(line)
                if (fields.size < 2) continue
                val id = fields[0].trim()
                val nom = fields[1].trim()
                if (id.isNotEmpty() && nom.isNotEmpty()) map[id] = nom
            }
        }
        return map
    }

    /** Parseur CSV minimal : gère les champs entre guillemets, sans dépendance externe. */
    internal fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
