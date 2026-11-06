package com.mee.noteapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mee.noteapp.R
import com.mee.noteapp.db.AppDatabase
import com.mee.noteapp.db.GeneralNote
import kotlinx.coroutines.launch

/**
 * Popup (AlertDialog) with the free-form general notes list — not tied to any specific
 * page or document. Shared by [MainActivity] and the page viewers so it can be opened
 * from the "+" icon on any of their toolbars. Tapping a note's text edits it; each note
 * shows its creation date/time and, if it has one, the page/PDF it was created from.
 *
 * [sourcePage] is the page/PDF name to stamp on any NEW note added while this dialog is
 * open (e.g. the MHTML page or PDF currently visible behind it) — pass null when there's
 * no specific page open (e.g. from the main screen).
 */
fun showGeneralNotesDialog(activity: AppCompatActivity, sourcePage: String? = null) {
    val db = AppDatabase.get(activity)
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_general_notes, null)
    val editNew = view.findViewById<EditText>(R.id.editNewNote)
    val btnAdd = view.findViewById<android.widget.Button>(R.id.btnAddGeneralNote)
    val txtEmpty = view.findViewById<TextView>(R.id.txtNoGeneralNotes)
    val recycler = view.findViewById<RecyclerView>(R.id.recyclerGeneralNotes)
    recycler.layoutManager = LinearLayoutManager(activity)

    var notes: List<GeneralNote> = emptyList()
    val adapter = object : RecyclerView.Adapter<GeneralNoteHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GeneralNoteHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_general_note, parent, false)
            return GeneralNoteHolder(v, db, activity)
        }
        override fun getItemCount() = notes.size
        override fun onBindViewHolder(holder: GeneralNoteHolder, position: Int) = holder.bind(notes[position])
    }
    recycler.adapter = adapter

    val dialog = AlertDialog.Builder(activity)
        .setTitle(R.string.general_notes)
        .setView(view)
        .setNegativeButton(R.string.cancel, null)
        .create()

    activity.lifecycleScope.launch {
        db.generalNoteDao().observeAll().collect { list ->
            notes = list
            txtEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    btnAdd.setOnClickListener {
        val text = editNew.text.toString().trim()
        if (text.isNotEmpty()) {
            activity.lifecycleScope.launch {
                val now = System.currentTimeMillis()
                db.generalNoteDao().insert(GeneralNote(text = text, updatedAt = now, createdAt = now, sourcePage = sourcePage))
            }
            editNew.setText("")
        }
    }

    dialog.show()
}

private class GeneralNoteHolder(
    v: View,
    private val db: AppDatabase,
    private val activity: AppCompatActivity
) : RecyclerView.ViewHolder(v) {
    private val text: TextView = v.findViewById(R.id.txtGeneralNote)
    private val date: TextView = v.findViewById(R.id.txtGeneralNoteDate)
    private val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteGeneralNote)

    fun bind(note: GeneralNote) {
        text.text = note.text
        val dateText = activity.getString(R.string.note_created_at, formatDateTime(note.createdAt))
        date.text = if (note.sourcePage.isNullOrBlank()) dateText
            else activity.getString(R.string.note_created_at_from, formatDateTime(note.createdAt), note.sourcePage)
        itemView.setOnClickListener { showEditDialog(note) }
        btnDelete.setOnClickListener {
            activity.lifecycleScope.launch { db.generalNoteDao().delete(note) }
        }
    }

    private fun showEditDialog(note: GeneralNote) {
        val editText = EditText(activity).apply {
            setText(note.text)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_note_title)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newText = editText.text.toString().trim()
                activity.lifecycleScope.launch {
                    if (newText.isBlank()) db.generalNoteDao().delete(note)
                    else db.generalNoteDao().update(note.copy(text = newText, updatedAt = System.currentTimeMillis()))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
