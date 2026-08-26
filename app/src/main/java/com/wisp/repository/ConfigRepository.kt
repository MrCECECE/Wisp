package com.wisp.repository

import android.content.Context
import com.wisp.R
import com.wisp.model.ConfigItem
import org.json.JSONArray
import org.json.JSONObject

class ConfigRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("wisp_prefs", Context.MODE_PRIVATE)
    private val CONFIG_PREFIX = "cfg_"

    fun saveConfigs(configs: List<ConfigItem>) {
        val previousCount = prefs.getInt("count", 0)
        prefs.edit().apply {
            putInt("count", configs.size)
            configs.forEachIndexed { i, c ->
                putString("${CONFIG_PREFIX}${i}_name", c.name)
                putString("${CONFIG_PREFIX}${i}_url", c.url)
                putLong("${CONFIG_PREFIX}${i}_date", c.lastUpdate?.time ?: -1L)
                putString("${CONFIG_PREFIX}${i}_raw", c.rawContent)
            }
            // Удаляем ключи удалённых конфигов, иначе они копятся вечно
            for (i in configs.size until previousCount) {
                remove("${CONFIG_PREFIX}${i}_name")
                remove("${CONFIG_PREFIX}${i}_url")
                remove("${CONFIG_PREFIX}${i}_date")
                remove("${CONFIG_PREFIX}${i}_raw")
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
            val lastUpdate = if (dateVal != -1L) java.util.Date(dateVal) else null
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

    /**
     * Поддерживаются форматы:
     * {"defaults": [{"name": "...", "url": "..."}]} и [{"name": ..., "url": ...}]
     */
    private fun parseDefaultsJson(json: String): List<ConfigItem> {
        val configs = mutableListOf<ConfigItem>()
        try {
            val root = try {
                JSONObject(json).optJSONArray("defaults")
            } catch (_: Exception) {
                null // это не объект, возможно top-level массив
            } ?: JSONArray(json)
            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    configs.add(ConfigItem(name, url, lastUpdate = null, rawContent = null))
                }
            }
        } catch (_: Exception) {
            // Невалидный JSON -> пустой список дефолтов
        }
        return configs
    }
}
