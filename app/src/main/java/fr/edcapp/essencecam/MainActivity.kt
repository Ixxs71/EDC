// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout

/**
 * Écran d'accueil de l'app EDC (Essence + Dash Cam) : bandeau de titre + 2 onglets qui basculent
 * entre EssenceFragment et DashcamFragment.
 *
 * Les deux fragments sont ajoutés une seule fois puis seulement montrés/cachés (show/hide) au
 * changement d'onglet — jamais recréés (replace()) — pour que les champs en cours de saisie
 * survivent à un aller-retour entre onglets sans avoir à valider (bouton Générer/Démarrer) avant
 * de changer d'onglet. Cf. Prefs : chaque champ persiste aussi en direct à chaque modification,
 * pour survivre en plus à une fermeture complète de l'app.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG_ESSENCE = "essence"
        private const val TAG_DASHCAM = "dashcam"
    }

    private lateinit var essenceFragment: Fragment
    private lateinit var dashcamFragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        findViewById<TextView>(R.id.btnAbout).setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        if (savedInstanceState == null) {
            essenceFragment = EssenceFragment()
            dashcamFragment = DashcamFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, dashcamFragment, TAG_DASHCAM)
                .hide(dashcamFragment)
                .add(R.id.fragmentContainer, essenceFragment, TAG_ESSENCE)
                .commit()
        } else {
            essenceFragment = supportFragmentManager.findFragmentByTag(TAG_ESSENCE)!!
            dashcamFragment = supportFragmentManager.findFragmentByTag(TAG_DASHCAM)!!
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val (show, hide) = if (tab.position == 0) essenceFragment to dashcamFragment else dashcamFragment to essenceFragment
                supportFragmentManager.beginTransaction().hide(hide).show(show).commit()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
}
