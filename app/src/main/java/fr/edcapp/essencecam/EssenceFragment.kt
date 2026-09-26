// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Onglet "Essence" : localise l'utilisateur, interroge l'API GPL autour de sa position, et écrit
 * le GPX pour import dans Organic Maps. Anciennement le contenu de MainActivity, extrait en
 * Fragment lors du passage à une appli à onglets (EDC = Essence + Dash Cam).
 */
class EssenceFragment : Fragment(R.layout.fragment_essence) {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                setStatus(getString(R.string.status_no_permission))
                return@registerForActivityResult
            }
            when (pendingAction) {
                PendingAction.GENERATE_GPX -> generate()
                PendingAction.VIEW_LIST -> startActivity(Intent(requireContext(), StationListActivity::class.java))
            }
        }

    private lateinit var spinnerFuel: Spinner
    private lateinit var spinnerRadius: Spinner
    private lateinit var editConso: EditText
    private lateinit var editQuantite: EditText
    private lateinit var editRoutingServer: EditText
    private lateinit var txtStatus: TextView

    private enum class PendingAction { GENERATE_GPX, VIEW_LIST }
    private var pendingAction = PendingAction.GENERATE_GPX

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerFuel = view.findViewById(R.id.spinnerFuel)
        spinnerFuel.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            FuelType.entries.map { it.label },
        )
        spinnerFuel.setSelection(FuelType.entries.indexOf(Prefs.getFuel(requireContext())))
        // Persisté à chaque changement (pas seulement au clic sur Générer/Voir la liste) : sinon
        // changer de carburant puis d'onglet, ou fermer l'app, perdait le choix.
        spinnerFuel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                Prefs.setFuel(requireContext(), FuelType.entries[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerRadius = view.findViewById(R.id.spinnerRadius)
        spinnerRadius.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            Prefs.RADIUS_OPTIONS_KM.map { "$it km" },
        )
        spinnerRadius.setSelection(Prefs.RADIUS_OPTIONS_KM.indexOf(Prefs.getRadiusKm(requireContext())).coerceAtLeast(0))
        spinnerRadius.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                Prefs.setRadiusKm(requireContext(), Prefs.RADIUS_OPTIONS_KM[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        editConso = view.findViewById(R.id.editConso)
        editConso.filters = arrayOf(DecimalDigitsInputFilter(maxDecimalDigits = 1))
        editConso.setText(String.format(Locale.FRANCE, "%.1f", Prefs.getConsommation(requireContext())))
        editConso.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s.toString().replace(',', '.').toDoubleOrNull()?.let { if (it > 0) Prefs.setConsommation(requireContext(), it) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editQuantite = view.findViewById(R.id.editQuantite)
        editQuantite.setText(Prefs.getQuantite(requireContext()).toInt().toString())
        editQuantite.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s.toString().replace(',', '.').toDoubleOrNull()?.let { if (it > 0) Prefs.setQuantite(requireContext(), it) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editRoutingServer = view.findViewById(R.id.editRoutingServer)
        editRoutingServer.setText(Prefs.getRoutingServer(requireContext()))
        editRoutingServer.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                Prefs.setRoutingServer(requireContext(), text)
                editRoutingServer.error =
                    if (text.isNotEmpty() && RoutingApi.normalizeServer(text) == null) getString(R.string.error_routing_server) else null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        txtStatus = view.findViewById(R.id.txtStatus)

        view.findViewById<Button>(R.id.btnGenerate).setOnClickListener {
            pendingAction = PendingAction.GENERATE_GPX
            if (hasLocationPermission()) generate()
            else requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        view.findViewById<Button>(R.id.btnViewList).setOnClickListener {
            if (!validateAndPersistSettings()) return@setOnClickListener
            if (hasLocationPermission()) {
                startActivity(Intent(requireContext(), StationListActivity::class.java))
            } else {
                pendingAction = PendingAction.VIEW_LIST
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun validateAndPersistSettings(): Boolean {
        val fuel = FuelType.entries[spinnerFuel.selectedItemPosition]
        val radiusKm = Prefs.RADIUS_OPTIONS_KM[spinnerRadius.selectedItemPosition]
        val conso = editConso.text.toString().replace(',', '.').toDoubleOrNull()
        val quantite = editQuantite.text.toString().replace(',', '.').toDoubleOrNull()
        if (conso == null || conso <= 0.0 || quantite == null || quantite <= 0.0) {
            setStatus(getString(R.string.status_error, "consommation et quantité doivent être des nombres positifs"))
            return false
        }
        Prefs.setFuel(requireContext(), fuel)
        Prefs.setRadiusKm(requireContext(), radiusKm)
        Prefs.setConsommation(requireContext(), conso)
        Prefs.setQuantite(requireContext(), quantite)
        return true
    }

    private fun generate() {
        if (!validateAndPersistSettings()) return
        val fuel = Prefs.getFuel(requireContext())
        val radiusKm = Prefs.getRadiusKm(requireContext())
        val conso = Prefs.getConsommation(requireContext())
        val quantite = Prefs.getQuantite(requireContext())
        val routingServer = Prefs.getRoutingServer(requireContext())

        lifecycleScope.launch {
            try {
                setStatus(getString(R.string.status_locating))
                val location = CurrentLocation.get(requireContext())
                    ?: throw IllegalStateException(getString(R.string.status_no_position))

                setStatus(getString(R.string.status_querying))
                val result = withContext(Dispatchers.IO) {
                    StationFetcher.fetch(location.latitude, location.longitude, radiusKm, fuel, conso, quantite, routingServer)
                }

                setStatus(getString(R.string.status_writing))
                val file = withContext(Dispatchers.IO) {
                    GpxWriter.writeToShareDir(requireContext(), result.ranked, fuel)
                }

                val note = if (result.distanceSource != DistanceSource.ROUTE) " (distance : ${result.distanceSource})" else ""
                setStatus(getString(R.string.status_done, result.ranked.size) + note)
                GpxWriter.share(requireContext(), file)
            } catch (e: Exception) {
                setStatus(getString(R.string.status_error, e.message ?: e.toString()))
            }
        }
    }

    private fun setStatus(text: String) {
        txtStatus.text = text
    }
}
