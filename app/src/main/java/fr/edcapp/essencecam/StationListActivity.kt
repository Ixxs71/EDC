// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Liste des stations classées par coût réel (prix + détour amorti), avec repli sur le
 * dernier résultat connu (Cache) si data.gouv.fr ou OSRM est indisponible au moment T.
 */
class StationListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var status: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_list)

        container = findViewById(R.id.containerStations)
        status = findViewById(R.id.txtListStatus)

        findViewById<MaterialButton>(R.id.btnRefreshList).setOnClickListener { refresh() }
        refresh()
    }

    private fun refresh() {
        container.removeAllViews()
        status.text = getString(R.string.status_locating)

        val fuel = Prefs.getFuel(this)
        val radiusKm = Prefs.getRadiusKm(this)
        val conso = Prefs.getConsommation(this)
        val quantite = Prefs.getQuantite(this)

        lifecycleScope.launch {
            try {
                val location = CurrentLocation.get(this@StationListActivity)
                    ?: throw IllegalStateException(getString(R.string.status_no_position))

                status.text = getString(R.string.status_querying)
                val result = withContext(Dispatchers.IO) {
                    StationFetcher.fetch(location.latitude, location.longitude, radiusKm, fuel, conso, quantite,
                        Prefs.getRoutingServer(this@StationListActivity))
                }
                Cache.save(this@StationListActivity, fuel, result.ranked, result.distanceSource)
                val enseignes = withContext(Dispatchers.IO) { EnseigneRepository.getMap(this@StationListActivity) }

                val note = if (result.distanceSource != DistanceSource.ROUTE) " — distance : ${result.distanceSource}" else ""
                status.text = getString(R.string.list_status, fuel.label, radiusKm, result.ranked.size, note)
                if (result.ranked.isEmpty()) status.text = getString(R.string.status_empty)
                renderList(result.ranked, fuel, enseignes)
            } catch (e: Exception) {
                val cached = Cache.load(this@StationListActivity, fuel)
                if (cached != null) {
                    val date = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(cached.timestampMillis))
                    status.text = getString(R.string.list_status_cached, e.message ?: e.toString(), date, cached.distanceSource)
                    val enseignes = withContext(Dispatchers.IO) { EnseigneRepository.getMap(this@StationListActivity) }
                    renderList(cached.ranked, fuel, enseignes)
                } else {
                    status.text = getString(R.string.status_error, e.message ?: e.toString())
                }
            }
        }
    }

    private fun renderList(ranked: List<RankedStation>, fuel: FuelType, enseignes: Map<String, String>) {
        container.removeAllViews()
        val staleThreshold = Freshness.averageAgeHours(ranked)
        ranked.forEachIndexed { index, r ->
            val stale = staleThreshold != null && Freshness.isStale(r.station.majIso, staleThreshold)
            container.addView(buildRow(r, fuel, stale, isBest = index == 0, enseignes[r.station.id]))
        }
    }

    private fun buildRow(r: RankedStation, fuel: FuelType, stale: Boolean, isBest: Boolean, enseigne: String?): View {
        val s = r.station
        val nomEnseigne = enseigne ?: getString(R.string.unknown_brand)

        val card = MaterialCardView(this)
        val cardParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        cardParams.bottomMargin = 20
        card.layoutParams = cardParams
        card.radius = 28f
        card.cardElevation = if (isBest) 8f else 3f
        card.setCardBackgroundColor(
            ContextCompat.getColor(this, if (stale) R.color.stale_bg else R.color.card_bg)
        )
        if (isBest) {
            card.strokeColor = ContextCompat.getColor(this, R.color.best_accent)
            card.strokeWidth = 4
        }
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            val uri = Uri.parse("geo:${s.lat},${s.lon}?q=${s.lat},${s.lon}(${Uri.encode(s.ville)})")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.status_no_map_app, Toast.LENGTH_LONG).show()
            }
        }

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(28, 24, 28, 24)

        if (isBest) {
            val badge = TextView(this)
            badge.text = getString(R.string.badge_best)
            badge.setTextColor(ContextCompat.getColor(this, R.color.best_accent))
            badge.textSize = 11f
            badge.setTypeface(null, Typeface.BOLD)
            badge.setPadding(0, 0, 0, 6)
            content.addView(badge)
        }

        val titleLine = TextView(this)
        val alerte = if (stale) "⚠️ " else ""
        titleLine.text = String.format(Locale.FRANCE, "%s%.3f€ %s · %s", alerte, s.prix, fuel.label, nomEnseigne)
        titleLine.textSize = 18f
        titleLine.setTypeface(null, Typeface.BOLD)
        titleLine.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        content.addView(titleLine)

        val addressLine = TextView(this)
        addressLine.text = "${s.adresse}, ${s.codePostal} ${s.ville}"
        addressLine.textSize = 14f
        addressLine.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        addressLine.setPadding(0, 4, 0, 0)
        content.addView(addressLine)

        val metaLine = TextView(this)
        metaLine.text = String.format(
            Locale.FRANCE, "%.1f km · coût effectif %.3f€/L (détour inclus)",
            r.distanceKm, r.effectivePricePerLiter,
        )
        metaLine.textSize = 13f
        metaLine.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        metaLine.setPadding(0, 6, 0, 0)
        content.addView(metaLine)

        card.addView(content)
        return card
    }
}
