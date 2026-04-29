package com.example.halliplanner

import android.content.Context

object SessionManager {
    private const val PREFS = "halli_session"
    private const val KEY_REMEMBER_UNTIL = "remember_until"
    private const val FIFTEEN_DAYS_MS = 15L * 24L * 60L * 60L * 1000L

    fun rememberForFifteenDays(context: Context) {
        val until = System.currentTimeMillis() + FIFTEEN_DAYS_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_REMEMBER_UNTIL, until)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_REMEMBER_UNTIL)
            .apply()
    }

    fun isRememberActive(context: Context): Boolean {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_REMEMBER_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun isExpiredRememberSession(context: Context): Boolean {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_REMEMBER_UNTIL, 0L)
        return until > 0L && until <= System.currentTimeMillis()
    }
}
