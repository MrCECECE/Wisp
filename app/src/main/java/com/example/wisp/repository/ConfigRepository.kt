package com.example.wisp.repository

import android.content.Context
import com.example.wisp.R  // ← ДОБАВИТЬ ЭТУ СТРОКУ
import com.example.wisp.model.ConfigItem
import java.util.Date

class ConfigRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("wisp_prefs", Context.MODE_PRIVATE)
    private val CONFIG_PREFIX = "cfg_"

    fun saveConfigs(configs: List<ConfigItem>) {
        prefs.edit().apply {
            putInt("count", configs.size)
            configs.forEachIndexed { i, c ->
                putString("${CONFIG_PREFIX}${i}_name", c.name)
                putString("${CONFIG_PREFIX}${i}_url", c.url)
                putLong("${CONFIG_PREFIX}${i}_date", c.lastUpdate?.time ?: -1L)
                putString("${CONFIG_PREFIX}${i}_raw", c.rawContent)
            }
            apply()
        }
    }

    fun loadConfigs(): List<ConfigItem> {
        val count = prefs.getInt("count", 0)
        return (0 until count).mapNotNull { i ->
            val name = prefs.getString("${CONFIG_PREFIX}${i}_name", "") ?: return@mapNotNull null
            val url = prefs.getString("${CONFIG_PREFIX}${i}_url", "") ?: return@mapNotNull null
            val dateVal = prefs.getLong("${CONFIG_PREFIX}${i}_date", -1L)
            val lastUpdate = if (dateVal != -1L) Date(dateVal) else null
            val raw = prefs.getString("${CONFIG_PREFIX}${i}_raw", null)
            ConfigItem(name, url, lastUpdate, raw)
        }
    }

    /**
     * Загружает предустановленные конфиги из res/raw/default_configs.json
     * Только если список пуст (первый запуск)
     */
    fun loadDefaultsIfNeeded(): Boolean {
        if (loadConfigs().isNotEmpty()) return false

        return try {
            val inputStream = context.resources.openRawResource(R.raw.default_configs)
            val json = inputStream.bufferedReader().use { it.readText() }
            val defaults = parseDefaultsJson(json)

            if (defaults.isNotEmpty()) {
                saveConfigs(defaults)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseDefaultsJson(json: String): List<ConfigItem> {
        val configs = mutableListOf<ConfigItem>()
        // Простой парсинг без внешних библиотек
        val regex = Regex("""\{\s*"name"\s*:\s*"([^"]+)"\s*,\s*"url"\s*:\s*"([^"]+)""")
        regex.findAll(json).forEach { match ->
            configs.add(ConfigItem(
                name = match.groupValues[1],
                url = match.groupValues[2],
                lastUpdate = null,
                rawContent = null
            ))
        }
        return configs
    }
}