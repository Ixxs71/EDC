// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Écran "À propos" : version, licence, vie privée (ce qui quitte le téléphone, et vers qui) et
 * attributions des données. Textes fixes dans strings.xml.
 */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<TextView>(R.id.txtAboutVersion).text = getString(R.string.about_version, BuildConfig.VERSION_NAME)

        if (Support.SPONSOR_URL.isNotBlank()) {
            findViewById<View>(R.id.aboutSupportBlock).visibility = View.VISIBLE
            findViewById<Button>(R.id.btnSupport).setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Support.SPONSOR_URL)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.about_support_no_browser, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
