// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

/**
 * Logique pure de la zone de vigilance (contrôle de vitesse), testée sans appareil.
 *
 * Cadre légal (voir CLAUDE.md pour les sources) : l'article R413-15 du code de la route interdit
 * d'avertir de la localisation précise d'un appareil de contrôle. Depuis le protocole d'accord du
 * 28 juillet 2011 entre le ministère de l'Intérieur et l'AFFTAC (repris par la certification
 * volontaire NF469), les assistants de conduite conformes (Coyote, Waze...) signalent une zone
 * large plutôt qu'un point : au moins 4 km sur autoroute, 2 km hors agglomération, 300 m en
 * agglomération. Cette app ne distingue pas le type de voie (donnée absente du jeu de données
 * utilisé) : elle applique le rayon le plus large des trois partout, donc jamais moins protecteur
 * qu'aucun des trois seuils.
 */
object VigilanceZone {
    /** Rayon par défaut, hors agglomération repérée : au moins aussi large que le seuil autoroute
     * du protocole de 2011 (4 km), donc large ou plus large que les seuils route et agglomération. */
    const val RADIUS_METERS = 4000.0
    const val EXIT_MARGIN_METERS = 6000.0

    /** Rayon resserré quand AgglomerationCheck signale une adresse connue toute proche (voir
     * VigilanceService) : dans la fourchette "300 à 500 m" du protocole pour l'agglomération —
     * la borne haute, par prudence (AgglomerationCheck n'est qu'une approximation, pas la
     * définition légale exacte). Utilisé seulement quand la classification urbaine est positive ;
     * tout doute (échec réseau, pas d'adresse proche) retombe sur le rayon large ci-dessus. */
    const val URBAN_RADIUS_METERS = 500.0
    const val URBAN_EXIT_MARGIN_METERS = 750.0

    /** Marge de sortie (hystérésis) : évite qu'un point pile à la limite fasse alterner l'alerte à
     * chaque mise à jour de position. */
    fun isInZone(distanceMeters: Double, radiusMeters: Double = RADIUS_METERS): Boolean =
        distanceMeters <= radiusMeters

    fun hasExitedZone(distanceMeters: Double, exitMarginMeters: Double = EXIT_MARGIN_METERS): Boolean =
        distanceMeters > exitMarginMeters
}
