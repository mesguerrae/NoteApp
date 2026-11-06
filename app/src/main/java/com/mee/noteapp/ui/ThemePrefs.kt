package com.mee.noteapp.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists the user's light/dark mode choice and applies it. Call [apply] as the very
 * first thing in every Activity's onCreate (before super.onCreate) so the chosen mode is
 * already active before the window is created.
 */
object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "night_mode"

    fun apply(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isNight(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Toggles between explicit light and explicit dark (ignores "follow system" once touched). */
    fun toggle(context: Context) {
        val newMode = if (isNight(context)) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }
}
