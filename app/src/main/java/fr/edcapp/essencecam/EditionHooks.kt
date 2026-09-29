// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Context

/** Édition publique : aucun écran caché, rien à relancer, mais l'onglet Vigilance (zone de
 * contrôle de vitesse au format légal, voir VigilanceZone.kt) est visible dans cette édition. */
object EditionHooks {
    fun openHiddenScreen(context: Context) {}

    fun onBootOrUpdate(context: Context) {}

    fun showsVigilanceTab(): Boolean = true
}
