// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.text.InputFilter
import android.text.Spanned

/** Limite la saisie à [maxDecimalDigits] chiffres après le séparateur décimal (. ou ,). */
class DecimalDigitsInputFilter(maxDecimalDigits: Int) : InputFilter {
    private val pattern = Regex("^\\d*([.,]\\d{0,$maxDecimalDigits})?$")

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int,
    ): CharSequence? {
        return if (isAllowed(dest.toString(), dstart, dend, source.subSequence(start, end))) null else ""
    }

    /** Le texte obtenu en remplaçant [dstart, dend) de [current] par [insert] est-il acceptable ? */
    internal fun isAllowed(current: String, dstart: Int, dend: Int, insert: CharSequence): Boolean {
        val proposed = current.substring(0, dstart) + insert + current.substring(dend)
        return proposed.isEmpty() || pattern.matches(proposed)
    }
}
