package com.example.wisp.utils
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("wisp_prefs", Context.MODE_PRIVATE)
    private val KEY_THEME = "theme_mode"

    fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(getSavedTheme())
    }

    fun applyTheme(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
        prefs.edit().putInt(KEY_THEME, mode).apply()
    }

    fun getSavedTheme(): Int = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
}