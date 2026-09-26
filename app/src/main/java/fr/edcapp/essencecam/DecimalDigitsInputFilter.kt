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
        val proposed = dest.toString().substring(0, dstart) +
            source.subSequence(start, end) +
            dest.toString().substring(dend)
        return if (proposed.isEmpty() || pattern.matches(proposed)) null else ""
    }
}
