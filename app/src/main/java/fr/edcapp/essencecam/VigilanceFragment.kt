// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Onglet "Vigilance" : pilote VigilanceService (zone de vigilance / contrôle de vitesse, jamais
 * une position précise — voir VigilanceZone.kt et CLAUDE.md pour le cadre légal). Édition publique
 * uniquement (voir EditionHooks.showsVigilanceTab), même architecture manuel/auto que les autres
 * modules de service de premier plan.
 */
class VigilanceFragment : Fragment(R.layout.fragment_vigilance) {

    private lateinit var checkSound: CheckBox
    private lateinit var checkAutoAA: CheckBox
    private lateinit var btnToggle: Button
    private lateinit var status: TextView

    private var notificationAsked = false
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(requireContext(), R.string.status_dashcam_no_notifications, Toast.LENGTH_LONG).show()
            requestStartVigilance()
        }
    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) requestStartVigilance() else status.text = getString(R.string.status_vigilance_no_permission)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkSound = view.findViewById(R.id.checkVigilanceSound)
        checkAutoAA = view.findViewById(R.id.checkVigilanceAutoAA)
        btnToggle = view.findViewById(R.id.btnToggleVigilance)
        status = view.findViewById(R.id.txtVigilanceStatus)

        checkSound.isChecked = Prefs.getVigilanceSoundEnabled(requireContext())
        checkSound.setOnCheckedChangeListener { _, checked -> Prefs.setVigilanceSoundEnabled(requireContext(), checked) }

        checkAutoAA.isChecked = Prefs.getVigilanceAutoAA(requireContext())
        checkAutoAA.setOnCheckedChangeListener { _, checked -> Prefs.setVigilanceAutoAA(requireContext(), checked) }

        btnToggle.setOnClickListener {
            if (VigilanceService.isRunning) stopVigilance() else requestStartVigilance()
        }
    }

    override fun onStart() {
        super.onStart()
        VigilanceService.statusListener = { text ->
            activity?.runOnUiThread { if (isAdded && VigilanceService.isRunning) status.text = text }
        }
        refreshToggleButtonLabel()
    }

    override fun onStop() {
        VigilanceService.statusListener = null
        super.onStop()
    }

    private fun refreshToggleButtonLabel() {
        val running = VigilanceService.isRunning
        btnToggle.text = getString(if (running) R.string.btn_stop_vigilance else R.string.btn_start_vigilance)
        checkAutoAA.isEnabled = !running
        checkSound.isEnabled = !running
        status.text = when {
            running && VigilanceService.statusText.isNotBlank() -> VigilanceService.statusText
            running && VigilanceService.isAutoMode -> getString(R.string.status_waiting_car)
            running -> getString(R.string.status_vigilance_on)
            else -> getString(R.string.status_vigilance_off)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestStartVigilance() {
        if (!hasLocationPermission()) {
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationAsked = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startVigilance(auto = checkAutoAA.isChecked)
    }

    private fun startVigilance(auto: Boolean) {
        val action = if (auto) VigilanceService.ACTION_START_AUTO else VigilanceService.ACTION_START
        val intent = Intent(requireContext(), VigilanceService::class.java).setAction(action)
        ContextCompat.startForegroundService(requireContext(), intent)
        btnToggle.text = getString(R.string.btn_stop_vigilance)
        status.text = getString(if (auto) R.string.status_waiting_car else R.string.status_vigilance_on)
        checkAutoAA.isEnabled = false
        checkSound.isEnabled = false
    }

    private fun stopVigilance() {
        val intent = Intent(requireContext(), VigilanceService::class.java).setAction(VigilanceService.ACTION_STOP)
        requireContext().startService(intent)
        btnToggle.text = getString(R.string.btn_start_vigilance)
        status.text = getString(R.string.status_vigilance_off)
        checkAutoAA.isEnabled = true
        checkSound.isEnabled = true
    }
}
