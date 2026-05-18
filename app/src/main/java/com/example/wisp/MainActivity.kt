package com.example.wisp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnDone: Button
    private lateinit var btnClose: Button
    private lateinit var btnThemeLight: Button
    private lateinit var btnThemeSystem: Button
    private lateinit var btnThemeDark: Button
    private lateinit var listContainer: LinearLayout
    private lateinit var btnAdd: Button

    private val configs = mutableListOf<ConfigItem>()
    private var currentConfig: ConfigItem? = null
    private var currentConfigView: View? = null
    private val PREFS = "wisp_prefs"
    private val KEY_THEME = "theme_mode"
    private val CONFIG_PREFIX = "cfg_"

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        btnDone = findViewById(R.id.btnDone)
        btnClose = findViewById(R.id.btnClose)
        btnThemeLight = findViewById(R.id.btnThemeLight)
        btnThemeSystem = findViewById(R.id.btnThemeSystem)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        listContainer = findViewById(R.id.listContainer)
        btnAdd = findViewById(R.id.btnAdd)

        setupThemeButtons()
        setupAddButton()
        setupWebView()
        loadConfigs()
    }

    // ================= THEMES =================
    private fun setupThemeButtons() {
        val currentMode = getSavedTheme()
        updateThemeButtons(currentMode)

        btnThemeLight.setOnClickListener { applyAppTheme(AppCompatDelegate.MODE_NIGHT_NO) }
        btnThemeSystem.setOnClickListener { applyAppTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        btnThemeDark.setOnClickListener { applyAppTheme(AppCompatDelegate.MODE_NIGHT_YES) }
    }

    private fun updateThemeButtons(mode: Int) {
        btnThemeLight.alpha = if (mode == AppCompatDelegate.MODE_NIGHT_NO) 1f else 0.5f
        btnThemeSystem.alpha = if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) 1f else 0.5f
        btnThemeDark.alpha = if (mode == AppCompatDelegate.MODE_NIGHT_YES) 1f else 0.5f
    }

    private fun applyAppTheme(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
        getPreferences(Context.MODE_PRIVATE).edit().putInt(KEY_THEME, mode).apply()
        updateThemeButtons(mode)
    }

    private fun applyTheme() {
        val mode = getSavedTheme()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun getSavedTheme(): Int =
        getPreferences(Context.MODE_PRIVATE).getInt(
            KEY_THEME,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

    // ================= ADD CONFIG =================
    private fun setupAddButton() {
        btnAdd.setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_config, null)
        val etName = dialogView.findViewById<EditText>(R.id.editTextName)
        val etUrl = dialogView.findViewById<EditText>(R.id.editTextUrl)

        AlertDialog.Builder(this)
            .setTitle("Добавить конфигурацию")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val url = etUrl.text.toString().trim()
                var name = etName.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (name.isEmpty()) name = "Конфигурация #${configs.size + 1}"
                    addConfig(name, url)
                } else {
                    Toast.makeText(this, "Введите ссылку", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addConfig(name: String, url: String) {
        val config = ConfigItem(name, url, null, null)
        configs.add(config)
        saveConfigs()
        renderConfig(config)
    }

    // ================= RENDER NEW CARD =================
    private fun renderConfig(config: ConfigItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_config, listContainer, false)

        view.findViewById<TextView>(R.id.tvName).text = config.name
        view.findViewById<TextView>(R.id.tvDate).text = if (config.lastUpdate != null)
            "Обновлено: ${formatDate(config.lastUpdate!!)}" else "Не обновлено"

        val relativeTime = if (config.lastUpdate != null) getRelativeTime(config.lastUpdate!!) else ""
        view.findViewById<TextView>(R.id.tvRelativeTime).text = relativeTime

        view.tag = config

        // Новые кнопки с иконками
        view.findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            openInWebView(config)
        }
        view.findViewById<ImageButton>(R.id.btnCopyHtml).setOnClickListener {
            copyHtmlToClipboard(config)
        }
        view.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            showConfigMenu(config, view)
        }

        listContainer.addView(view)
    }

    private fun copyHtmlToClipboard(config: ConfigItem) {
        val raw = config.rawContent
        if (raw != null) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("raw_html", raw))
            Toast.makeText(this, "HTML скопирован", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "HTML не сохранен. Сначала обновите конфиг.", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= WEBVIEW =================
    private fun openInWebView(config: ConfigItem) {
        currentConfig = config
        currentConfigView = listContainer.findViewWithTag<View>(config)
        var url = config.url
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        webView.loadUrl(url)
        webView.visibility = View.VISIBLE
        btnDone.visibility = View.VISIBLE
        btnClose.visibility = View.VISIBLE
        listContainer.visibility = View.GONE
        btnAdd.visibility = View.GONE
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val msg = "Ошибка загрузки страницы: ${error?.description}"
                Log.d("Wisp", msg)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                val msg = "Ошибка загрузки страницы: $description"
                Log.d("Wisp", msg)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript("(function() { return document.documentElement.outerHTML; })()") { html ->
                    html?.let { currentConfig?.rawContent = unescapeHtml(it) }
                }
            }
        }
        webView.visibility = View.GONE
        btnDone.visibility = View.GONE
        btnClose.visibility = View.GONE
        btnDone.setOnClickListener { saveAndCopyPage() }
        btnClose.setOnClickListener { exitWebView() }
    }

    private fun unescapeHtml(html: String): String {
        var result = html
        result = result.replace("\\u003C", "<")
        result = result.replace("\\u003E", ">")
        result = result.replace("\\u0022", "\"")
        result = result.replace("\\u0026", "&")
        result = result.replace("\\n", "\n")
        result = result.replace("\\t", "\t")
        result = result.removeSurrounding("\"")
        return result
    }

    private fun saveAndCopyPage() {
        val content = currentConfig?.rawContent
        if (content == null) {
            Toast.makeText(this, "Ошибка: страница не загружена. Проверьте подключение к интернету.", Toast.LENGTH_LONG).show()
            return
        }
        val urls = extractVpnUrls(content)
        if (urls.isNotEmpty()) {
            val result = urls.joinToString("\n")
            currentConfig?.rawContent = result
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("vpn_urls", result))
            currentConfig?.lastUpdate = Date()
            saveConfigs()
            currentConfigView?.findViewById<TextView>(R.id.tvDate)?.text =
                "Обновлено: ${formatDate(currentConfig?.lastUpdate!!)}"
            // Обновляем и относительное время
            currentConfigView?.findViewById<TextView>(R.id.tvRelativeTime)?.text =
                getRelativeTime(currentConfig?.lastUpdate!!)
            exitWebView()
            Toast.makeText(this, "Извлечено ссылок: ${urls.size}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "VPN ссылки не найдены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractVpnUrls(html: String): List<String> {
        val urls = mutableSetOf<String>()
        val preRegex = Regex("<pre[^>]*>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)
        val preMatch = preRegex.find(html) ?: return emptyList()
        val preContent = preMatch.groupValues[1]

        preContent.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
                var clean = trimmed
                    .replace("&amp;", "&")
                    .replace("%5C", "")
                    .replace("%22", "")
                    .replace("%2C", ",")
                clean = Regex("headerType=(none[,%]*)", RegexOption.IGNORE_CASE).replace(clean, "headerType=none")
                urls.add(clean)
            }
        }
        return urls.toList()
    }

    private fun exitWebView() {
        webView.visibility = View.GONE
        btnDone.visibility = View.GONE
        btnClose.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        btnAdd.visibility = View.VISIBLE
    }

    // ================= EDIT & MENU =================
    private fun showEditDialog(config: ConfigItem, parentView: View) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_config, null)
        val etName = dialogView.findViewById<EditText>(R.id.editTextName)
        val etUrl = dialogView.findViewById<EditText>(R.id.editTextUrl)
        etName.setText(config.name)
        etUrl.setText(config.url)

        AlertDialog.Builder(this)
            .setTitle("Редактировать")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = etName.text.toString().trim()
                val newUrl = etUrl.text.toString().trim()
                if (newName.isNotEmpty() && newUrl.isNotEmpty()) {
                    config.name = newName
                    config.url = newUrl
                    parentView.findViewById<TextView>(R.id.tvName).text = newName
                    saveConfigs()
                    Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showConfigMenu(config: ConfigItem, parentView: View) {
        val items = arrayOf("Изменить конфиг", "Копировать ссылку конфига", "Удалить")
        AlertDialog.Builder(this)
            .setTitle(config.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showEditDialog(config, parentView)
                    1 -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("config_url", config.url))
                        Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        configs.remove(config)
                        listContainer.removeView(parentView)
                        saveConfigs()
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    // ================= UTILS & PERSISTENCE =================
    private fun formatDate(date: Date): String =
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(date)

    private fun getRelativeTime(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes ${plural(minutes, "минуту", "минуты", "минут")} назад"
            hours < 24 -> "$hours ${plural(hours, "час", "часа", "часов")} назад"
            days < 7 -> "$days ${plural(days, "день", "дня", "дней")} назад"
            else -> formatDate(date)
        }
    }

    private fun plural(n: Long, one: String, few: String, many: String): String {
        val mod10 = (n % 10).toInt()
        val mod100 = (n % 100).toInt()
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    private fun saveConfigs() {
        val editor = getPreferences(Context.MODE_PRIVATE).edit()
        editor.putInt("count", configs.size)
        configs.forEachIndexed { i, c ->
            editor.putString("${CONFIG_PREFIX}${i}_name", c.name)
            editor.putString("${CONFIG_PREFIX}${i}_url", c.url)
            editor.putLong("${CONFIG_PREFIX}${i}_date", c.lastUpdate?.time ?: -1L)
            editor.putString("${CONFIG_PREFIX}${i}_raw", c.rawContent)
        }
        editor.apply()
    }

    private fun loadConfigs() {
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        for (i in 0 until count) {
            val name = prefs.getString("${CONFIG_PREFIX}${i}_name", "") ?: ""
            val url = prefs.getString("${CONFIG_PREFIX}${i}_url", "") ?: ""
            val dateVal = prefs.getLong("${CONFIG_PREFIX}${i}_date", -1L)
            val lastUpdate = if (dateVal != -1L) Date(dateVal) else null
            val raw = prefs.getString("${CONFIG_PREFIX}${i}_raw", null)

            val config = ConfigItem(name, url, lastUpdate, raw)
            configs.add(config)
            renderConfig(config)
        }
    }
}

data class ConfigItem(
    var name: String,
    var url: String,
    var lastUpdate: Date?,
    var rawContent: String? = null
)
