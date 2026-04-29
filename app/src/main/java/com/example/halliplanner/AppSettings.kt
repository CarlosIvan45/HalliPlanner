package com.example.halliplanner

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppSettings {
    private const val PREFS = "halli_settings"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_SEEN_ALERTS = "seen_completion_alerts"

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode(context)) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()

        AppCompatDelegate.setDefaultNightMode(
            if (enabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    fun isDarkMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)
    }

    fun unseenCompletionAlerts(context: Context, ids: Set<String>): Set<String> {
        val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SEEN_ALERTS, emptySet())
            .orEmpty()
        return ids.filterNot { seen.contains(it) }.toSet()
    }

    fun markCompletionAlertsSeen(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(KEY_SEEN_ALERTS, emptySet()).orEmpty().toMutableSet()
        updated.addAll(ids)
        prefs.edit().putStringSet(KEY_SEEN_ALERTS, updated).apply()
    }
}
