// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.CamcorderProfile
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.util.Locale

/**
 * Onglet "Dash cam" : pilote DashcamService (qualité, durée de clip, plafond de stockage, mode
 * auto AA). Anciennement DashcamActivity, extrait en Fragment lors du passage à une appli à
 * onglets (EDC = Essence + Dash Cam).
 */
class DashcamFragment : Fragment(R.layout.fragment_dashcam) {

    private lateinit var dirStatus: TextView
    private lateinit var btnOpenFolder: Button
    private lateinit var dashcamStatus: TextView
    private lateinit var retentionInfo: TextView
    private lateinit var spinnerQuality: Spinner
    private lateinit var editClipMinutes: EditText
    private lateinit var editCapGb: EditText
    private lateinit var checkAutoAA: CheckBox
    private lateinit var btnToggle: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) requestStartDashcam() else dashcamStatus.text = getString(R.string.status_dashcam_no_permission)
        }

    // Android 13+ : sans cette permission, ni le statut du service ni la notification "Dashcam
    // désarmée" ne s'affichent. Demandée une fois par lancement ; un refus n'empêche pas l'enregistrement.
    private var notificationAsked = false
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(requireContext(), R.string.status_dashcam_no_notifications, Toast.LENGTH_LONG).show()
            requestStartDashcam()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dirStatus = view.findViewById(R.id.txtDashcamDirStatus)
        btnOpenFolder = view.findViewById(R.id.btnOpenDashcamFolder)
        btnOpenFolder.setOnClickListener { startActivity(Intent(requireContext(), DashcamVideosActivity::class.java)) }
        dashcamStatus = view.findViewById(R.id.txtDashcamStatus)
        retentionInfo = view.findViewById(R.id.txtDashcamRetention)
        spinnerQuality = view.findViewById(R.id.spinnerDashcamQuality)
        editClipMinutes = view.findViewById(R.id.editDashcamClipMinutes)
        editCapGb = view.findViewById(R.id.editDashcamCapGb)
        checkAutoAA = view.findViewById(R.id.checkDashcamAutoAA)
        btnToggle = view.findViewById(R.id.btnToggleDashcam)

        val qualityLabels = Prefs.DASHCAM_QUALITY_OPTIONS.map { it.second }
        spinnerQuality.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, qualityLabels)
        val currentQualityKey = Prefs.getDashcamQuality(requireContext())
        val currentIndex = Prefs.DASHCAM_QUALITY_OPTIONS.indexOfFirst { it.first == currentQualityKey }.coerceAtLeast(0)
        spinnerQuality.setSelection(currentIndex)

        editClipMinutes.setText(Prefs.getDashcamClipMinutes(requireContext()).toString())
        // Persisté à chaque frappe (pas seulement au clic sur Démarrer) : sinon changer un réglage
        // puis d'onglet, ou fermer l'app, perdait la valeur.
        editClipMinutes.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s.toString().toIntOrNull()?.let { if (it > 0) Prefs.setDashcamClipMinutes(requireContext(), it) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editCapGb.setText(Prefs.getDashcamStorageCapGb(requireContext()).toString())

        checkAutoAA.isChecked = Prefs.getDashcamAutoAA(requireContext())
        checkAutoAA.setOnCheckedChangeListener { _, checked -> Prefs.setDashcamAutoAA(requireContext(), checked) }

        btnToggle.setOnClickListener {
            if (DashcamService.isRunning) stopDashcam() else requestStartDashcam()
        }

        spinnerQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                Prefs.setDashcamQuality(requireContext(), Prefs.DASHCAM_QUALITY_OPTIONS[position].first)
                updateRetentionEstimate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val capWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s.toString().toIntOrNull()?.let { if (it > 0) Prefs.setDashcamStorageCapGb(requireContext(), it) }
                updateRetentionEstimate()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        editCapGb.addTextChangedListener(capWatcher)

        val dir = DashcamFiles.dir(requireContext())
        val sizeGb = DashcamFiles.clips(dir).sumOf { it.length() } / 1_000_000_000.0
        dirStatus.text = String.format(Locale.FRANCE, "Dossier : %s — %.2f Go utilisés", dir.absolutePath, sizeGb)

        updateRetentionEstimate()
    }

    override fun onStart() {
        super.onStart()
        // Statut suivi en direct (attente / enregistrement / erreur) tant que l'onglet est visible.
        DashcamService.statusListener = { text ->
            activity?.runOnUiThread { if (isAdded && DashcamService.isRunning) dashcamStatus.text = text }
        }
        refreshToggleButtonLabel()
    }

    override fun onStop() {
        DashcamService.statusListener = null
        super.onStop()
    }

    private fun refreshToggleButtonLabel() {
        val running = DashcamService.isRunning
        btnToggle.text = getString(if (running) R.string.btn_stop_dashcam else R.string.btn_start_dashcam)
        checkAutoAA.isEnabled = !running
        spinnerQuality.isEnabled = !running
        editClipMinutes.isEnabled = !running
        editCapGb.isEnabled = !running
        dashcamStatus.text = when {
            running && DashcamService.statusText.isNotBlank() -> DashcamService.statusText
            running && DashcamService.isAutoMode -> getString(R.string.status_waiting_car)
            running -> getString(R.string.status_dashcam_on)
            // Armée par l'utilisateur mais plus en marche : Android a tué l'app (le bouton est
            // repassé sur "Démarrer" sans que l'utilisateur y soit pour rien).
            Prefs.getDashcamArmed(requireContext()) -> getString(R.string.status_dashcam_killed)
            else -> getString(R.string.status_dashcam_off)
        }
    }

    /**
     * Estime le temps d'enregistrement avant que les premiers clips ne soient écrasés, à partir
     * du débit vidéo RÉEL de l'appareil pour la qualité choisie (CamcorderProfile.videoBitRate) —
     * pas une valeur devinée : chaque modèle de téléphone a son propre profil d'encodage. La durée
     * de clip n'entre pas dans le calcul (elle ne change que le découpage, pas la capacité totale) ;
     * seuls le plafond de stockage et la qualité comptent : temps = plafond (bits) / débit (bit/s).
     */
    private fun updateRetentionEstimate() {
        val capGb = editCapGb.text.toString().toIntOrNull()
        if (capGb == null || capGb <= 0) {
            retentionInfo.text = ""
            return
        }
        val qualityKey = Prefs.DASHCAM_QUALITY_OPTIONS[spinnerQuality.selectedItemPosition].first
        val camcorderQuality = when (qualityKey) {
            "SD" -> CamcorderProfile.QUALITY_480P
            "FHD" -> CamcorderProfile.QUALITY_1080P
            else -> CamcorderProfile.QUALITY_720P
        }
        @Suppress("DEPRECATION")
        val hasProfile = CamcorderProfile.hasProfile(0, camcorderQuality)
        if (!hasProfile) {
            retentionInfo.text = getString(R.string.status_retention_unavailable)
            return
        }
        @Suppress("DEPRECATION")
        val profile = CamcorderProfile.get(0, camcorderQuality)
        val bitsPerSecond = profile.videoBitRate.toLong()
        if (bitsPerSecond <= 0) {
            retentionInfo.text = ""
            return
        }
        val totalSeconds = DashcamMath.retentionSeconds(capGb, bitsPerSecond)
        retentionInfo.text = if (totalSeconds == null) "" else
            getString(R.string.status_retention_estimate, DashcamMath.formatDuration(totalSeconds))
    }

    /** Valide et enregistre qualité/durée de clip/plafond depuis les champs. Retourne false si invalide. */
    private fun validateAndPersistSettings(): Boolean {
        val qualityKey = Prefs.DASHCAM_QUALITY_OPTIONS[spinnerQuality.selectedItemPosition].first
        val clipMinutes = editClipMinutes.text.toString().toIntOrNull()
        val capGb = editCapGb.text.toString().toIntOrNull()
        if (clipMinutes == null || clipMinutes <= 0 || capGb == null || capGb <= 0) {
            dashcamStatus.text = getString(R.string.status_error, "durée de clip et plafond de stockage doivent être des nombres positifs")
            return false
        }
        Prefs.setDashcamQuality(requireContext(), qualityKey)
        Prefs.setDashcamClipMinutes(requireContext(), clipMinutes)
        Prefs.setDashcamStorageCapGb(requireContext(), capGb)
        return true
    }

    private fun requestStartDashcam() {
        if (!validateAndPersistSettings()) return

        val cameraOk = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!cameraOk) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
        startDashcam(auto = checkAutoAA.isChecked)
    }

    private fun startDashcam(auto: Boolean) {
        val action = if (auto) DashcamService.ACTION_START_AUTO else DashcamService.ACTION_START
        val intent = Intent(requireContext(), DashcamService::class.java).setAction(action)
        ContextCompat.startForegroundService(requireContext(), intent)
        btnToggle.text = getString(R.string.btn_stop_dashcam)
        dashcamStatus.text = getString(if (auto) R.string.status_waiting_car else R.string.status_dashcam_on)
        checkAutoAA.isEnabled = false
        spinnerQuality.isEnabled = false
        editClipMinutes.isEnabled = false
        editCapGb.isEnabled = false
    }

    private fun stopDashcam() {
        val intent = Intent(requireContext(), DashcamService::class.java).setAction(DashcamService.ACTION_STOP)
        requireContext().startService(intent)
        btnToggle.text = getString(R.string.btn_start_dashcam)
        dashcamStatus.text = getString(R.string.status_dashcam_off)
        checkAutoAA.isEnabled = true
        spinnerQuality.isEnabled = true
        editClipMinutes.isEnabled = true
        editCapGb.isEnabled = true
    }
}
