// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

/**
 * Les 6 carburants suivis par l'API "Prix des carburants en France - Flux instantane v2".
 * apiField = préfixe des champs API (ex. "gplc_prix", "gplc_maj").
 */
enum class FuelType(val apiField: String, val label: String) {
    GAZOLE("gazole", "Gazole"),
    SP95("sp95", "SP95"),
    SP98("sp98", "SP98"),
    E10("e10", "E10"),
    E85("e85", "E85"),
    GPLC("gplc", "GPLc");

    companion object {
        fun fromName(name: String?): FuelType =
            entries.firstOrNull { it.name == name } ?: GPLC
    }
}
