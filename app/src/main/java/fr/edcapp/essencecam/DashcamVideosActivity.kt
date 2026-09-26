// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 EDC contributors

package fr.edcapp.essencecam

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Liste des clips de la dashcam, du plus récent au plus ancien (tri par nom décroissant : les noms
 * sont AAAAMMJJ_HHMMSS, donc l'ordre alphabétique est l'ordre chronologique). Un gestionnaire de
 * fichiers externe ne permet pas d'imposer ce tri à l'ouverture — d'où cet écran. Toucher un clip
 * le lit dans le lecteur vidéo du téléphone ; un appui long l'envoie à l'application de son choix
 * (copie, messagerie, cloud). Les trois points ouvrent le même choix avec la suppression (confirmée).
 */
class DashcamVideosActivity : AppCompatActivity() {

    private val nameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE)
    private val labelFormat = SimpleDateFormat("EEE dd/MM/yyyy  HH:mm:ss", Locale.FRANCE)

    private lateinit var summary: TextView
    private lateinit var list: ListView
    private var clips: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashcam_videos)

        summary = findViewById(R.id.txtVideosSummary)
        list = findViewById(R.id.listVideos)
        list.emptyView = findViewById(R.id.txtVideosEmpty)

        list.setOnItemClickListener { _, _, position, _ -> play(clips[position]) }
        list.setOnItemLongClickListener { _, _, position, _ -> share(clips[position]); true }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        clips = DashcamFiles.clips(DashcamFiles.dir(this)).sortedByDescending { it.name }
        list.adapter = object : ArrayAdapter<File>(this, R.layout.item_video, clips) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
                val file = clips[position]
                row.findViewById<TextView>(R.id.txtVideoLabel).text = label(file)
                row.findViewById<View>(R.id.btnVideoMenu).setOnClickListener { showMenu(it, file) }
                return row
            }
        }
        summary.text = String.format(
            Locale.FRANCE, "%d clip(s) — %.2f Go", clips.size, clips.sumOf { it.length() } / 1_000_000_000.0,
        )
    }

    private fun protectedNames(): Set<String> = Prefs.getProtectedClips(this)

    private fun toggleProtection(file: File) {
        val names = protectedNames()
        Prefs.setProtectedClips(this, if (file.name in names) names - file.name else names + file.name)
        refresh()
    }

    private fun label(file: File): String {
        val lock = if (file.name in protectedNames()) "🔒 " else ""
        val date = runCatching { nameFormat.parse(file.nameWithoutExtension) }.getOrNull()
        val title = if (date != null) labelFormat.format(date) else file.name
        return String.format(Locale.FRANCE, "%s%s   —   %d Mo", lock, title, file.length() / 1_000_000)
    }

    private fun play(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.status_dashcam_no_video_player, Toast.LENGTH_LONG).show()
        }
    }

    /** Appui long : envoie le clip à l'application choisie (Fichiers, messagerie, cloud...). */
    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, file.name))
    }

    private fun showMenu(anchor: View, file: File) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_PLAY, 0, R.string.menu_video_play)
            menu.add(0, MENU_SEND, 1, R.string.menu_video_send)
            menu.add(0, MENU_PROTECT, 2, if (file.name in protectedNames()) R.string.menu_video_unprotect else R.string.menu_video_protect)
            menu.add(0, MENU_DELETE, 3, R.string.menu_video_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    MENU_PLAY -> play(file)
                    MENU_SEND -> share(file)
                    MENU_PROTECT -> toggleProtection(file)
                    MENU_DELETE -> confirmDelete(file)
                }
                true
            }
            show()
        }
    }

    /** Suppression définitive, après confirmation ; le clip en cours d'enregistrement est refusé. */
    private fun confirmDelete(file: File) {
        if (file.name == DashcamService.currentClipName) {
            Toast.makeText(this, R.string.status_video_recording_now, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_video_delete_title)
            .setMessage(getString(
                if (file.name in protectedNames()) R.string.dialog_video_delete_protected_message else R.string.dialog_video_delete_message,
                label(file),
            ))
            .setPositiveButton(R.string.menu_video_delete) { _, _ ->
                if (DashcamFiles.isClip(file)) file.delete()
                Prefs.setProtectedClips(this, protectedNames() - file.name)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val MENU_PLAY = 1
        const val MENU_SEND = 2
        const val MENU_PROTECT = 3
        const val MENU_DELETE = 4
    }
}
