package com.mee.noteapp.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.mee.noteapp.R
import com.mee.noteapp.db.NoteStyle

/**
 * Shows the "write a note" popup, used by both the MHTML and PDF viewers, with a choice
 * between the normal style and the red "muy importante" style. When [createdAt] is non-null
 * (editing an existing note) its date/time is shown read-only; it's null for brand-new notes.
 */
fun showNoteStyleDialog(
    context: Context,
    initialText: String?,
    initialStyle: String,
    createdAt: Long?,
    onSaved: (text: String, style: String) -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_note, null)
    val edit = view.findViewById<EditText>(R.id.editNoteText)
    val radioGroup = view.findViewById<RadioGroup>(R.id.radioNoteStyle)
    val txtCreatedAt = view.findViewById<TextView>(R.id.txtNoteCreatedAt)
    edit.setText(initialText ?: "")
    radioGroup.check(
        if (initialStyle == NoteStyle.IMPORTANT) R.id.radioStyleImportant else R.id.radioStyleGeneral
    )
    if (createdAt != null) {
        txtCreatedAt.visibility = View.VISIBLE
        txtCreatedAt.text = context.getString(R.string.note_created_at, formatDateTime(createdAt))
    } else {
        txtCreatedAt.visibility = View.GONE
    }

    AlertDialog.Builder(context)
        .setView(view)
        .setPositiveButton(R.string.save) { _, _ ->
            val style = if (radioGroup.checkedRadioButtonId == R.id.radioStyleImportant) NoteStyle.IMPORTANT else NoteStyle.GENERAL
            onSaved(edit.text.toString(), style)
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
