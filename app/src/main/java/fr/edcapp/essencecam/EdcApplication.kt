// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Bascule jour/nuit automatique : suit le réglage système (lui-même automatique si l'utilisateur
 * a activé "Basculer automatiquement" dans Paramètres > Affichage > Thème sombre sur le téléphone).
 * C'est le comportement par défaut d'AppCompat, rendu explicite ici plutôt qu'implicite.
 */
class EdcApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}
