package com.mee.noteapp.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats a timestamp (epoch millis) as "dd/MM/yyyy HH:mm" for display next to notes. */
fun formatDateTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
