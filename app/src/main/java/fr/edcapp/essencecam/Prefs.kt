// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context

/**
 * Préférences partagées entre les écrans de l'app : carburant sélectionné, consommation du
 * véhicule, quantité à acheter et rayon de recherche (utilisés pour le classement par coût réel).
 */
object Prefs {
    internal const val FILE = "edc_prefs"
    private const val KEY_FUEL = "fuel_type"
    private const val KEY_CONSO = "conso_l_100km"
    private const val KEY_QUANTITE = "quantite_litres"
    private const val KEY_RADIUS = "radius_km"
    private const val KEY_DASHCAM_QUALITY = "dashcam_quality"
    private const val KEY_DASHCAM_CLIP_MIN = "dashcam_clip_min"
    private const val KEY_DASHCAM_CAP_GB = "dashcam_cap_gb"
    private const val KEY_DASHCAM_AUTO_AA = "dashcam_auto_aa"
    private const val KEY_DASHCAM_ARMED = "dashcam_armed"
    private const val KEY_DASHCAM_PROTECTED = "dashcam_protected_clips"
    private const val KEY_ROUTING_SERVER = "routing_server"

    private const val DEFAULT_CONSO = 10.0f
    private const val DEFAULT_QUANTITE = 40.0f
    private const val DEFAULT_RADIUS = 25
    private const val DEFAULT_DASHCAM_QUALITY = "HD"
    private const val DEFAULT_DASHCAM_CLIP_MIN = 3
    private const val DEFAULT_DASHCAM_CAP_GB = 8

    /** Valeurs possibles pour KEY_DASHCAM_QUALITY, mappées vers androidx.camera.video.Quality
     * uniquement côté DashcamService (Prefs reste indépendant de CameraX). */
    val DASHCAM_QUALITY_OPTIONS = listOf("SD" to "480p", "HD" to "720p", "FHD" to "1080p")

    val RADIUS_OPTIONS_KM = listOf(5, 10, 25, 50)

    fun getFuel(context: Context): FuelType {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return FuelType.fromName(prefs.getString(KEY_FUEL, null))
    }

    fun setFuel(context: Context, fuel: FuelType) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FUEL, fuel.name)
            .apply()
    }

    /** Consommation du véhicule en GPL, en L/100km. */
    fun getConsommation(context: Context): Double =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getFloat(KEY_CONSO, DEFAULT_CONSO).toDouble()

    fun setConsommation(context: Context, value: Double) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_CONSO, value.toFloat())
            .apply()
    }

    /** Quantité de carburant que l'utilisateur compte acheter, en litres. */
    fun getQuantite(context: Context): Double =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getFloat(KEY_QUANTITE, DEFAULT_QUANTITE).toDouble()

    fun setQuantite(context: Context, value: Double) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_QUANTITE, value.toFloat())
            .apply()
    }

    /** Rayon de recherche autour de la position courante, en km (voir RADIUS_OPTIONS_KM). */
    fun getRadiusKm(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_RADIUS, DEFAULT_RADIUS)

    fun setRadiusKm(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RADIUS, value)
            .apply()
    }

    /**
     * Adresse https d'un serveur OSRM pour les distances routières. Jamais saisie = valeur par défaut de
     * l'édition (BuildConfig.DEFAULT_ROUTING_SERVER, vide dans l'édition publique) ; saisie puis vidée =
     * aucun serveur, distances estimées sur le téléphone.
     */
    fun getRoutingServer(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_ROUTING_SERVER, null) ?: BuildConfig.DEFAULT_ROUTING_SERVER

    fun setRoutingServer(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTING_SERVER, value.trim())
            .apply()
    }

    /** Qualité vidéo dashcam : "SD"/"HD"/"FHD" (voir DASHCAM_QUALITY_OPTIONS). */
    fun getDashcamQuality(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_DASHCAM_QUALITY, DEFAULT_DASHCAM_QUALITY)
            ?: DEFAULT_DASHCAM_QUALITY

    fun setDashcamQuality(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DASHCAM_QUALITY, value)
            .apply()
    }

    /** Durée de chaque clip en boucle, en minutes — au-delà, le clip suivant démarre et le plus
     * ancien est supprimé si le plafond de stockage (getDashcamStorageCapGb) est dépassé. */
    fun getDashcamClipMinutes(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_DASHCAM_CLIP_MIN, DEFAULT_DASHCAM_CLIP_MIN)

    fun setDashcamClipMinutes(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DASHCAM_CLIP_MIN, value)
            .apply()
    }

    /** Plafond de stockage occupé par les clips dashcam, en Go — les clips les plus anciens sont
     * supprimés automatiquement au-delà (voir DashcamService.enforceStorageCap). */
    fun getDashcamStorageCapGb(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_DASHCAM_CAP_GB, DEFAULT_DASHCAM_CAP_GB)

    fun setDashcamStorageCapGb(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DASHCAM_CAP_GB, value)
            .apply()
    }

    /** Mode auto dashcam : démarre/arrête l'enregistrement selon la connexion Android Auto. */
    fun getDashcamAutoAA(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DASHCAM_AUTO_AA, false)

    fun setDashcamAutoAA(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DASHCAM_AUTO_AA, value)
            .apply()
    }

    /** Noms des clips protégés : jamais supprimés par la boucle, non comptés dans le plafond. */
    fun getProtectedClips(context: Context): Set<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(KEY_DASHCAM_PROTECTED, emptySet())?.toSet() ?: emptySet()

    fun setProtectedClips(context: Context, names: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DASHCAM_PROTECTED, names)
            .apply()
    }

    /** true tant que l'utilisateur n'a pas arrêté la dashcam lui-même (bouton, notification). Reste
     * true si Android tue le processus : permet de distinguer "jamais démarrée" de "arrêtée par
     * Android" pour afficher le bon message et proposer de la réarmer. */
    fun getDashcamArmed(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DASHCAM_ARMED, false)

    fun setDashcamArmed(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DASHCAM_ARMED, value)
            .apply()
    }
}
