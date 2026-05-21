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
import com.example.wisp.model.ConfigItem
import com.example.wisp.repository.ConfigRepository
import com.example.wisp.utils.DateUtils
import com.example.wisp.utils.HtmlParser
import com.example.wisp.utils.ThemeManager
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var btnDone: Button
    private lateinit var btnClose: Button
    private lateinit var btnThemeLight: Button
    private lateinit var btnThemeSystem: Button
    private lateinit var btnThemeDark: Button
    private lateinit var listContainer: LinearLayout
    private lateinit var btnAdd: Button

    private val themeManager by lazy { ThemeManager(this) }
    private val configRepository by lazy { ConfigRepository(this) }
    private val configs = mutableListOf<ConfigItem>()
    private var currentConfig: ConfigItem? = null
    private var currentConfigView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager.applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupThemeButtons()
        setupAddButton()
        setupWebView()

        // 🔥 Загружаем предустановленные конфиги при первом запуске
        configRepository.loadDefaultsIfNeeded()

        loadConfigs()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        btnDone = findViewById(R.id.btnDone)
        btnClose = findViewById(R.id.btnClose)
        btnThemeLight = findViewById(R.id.btnThemeLight)
        btnThemeSystem = findViewById(R.id.btnThemeSystem)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        listContainer = findViewById(R.id.listContainer)
        btnAdd = findViewById(R.id.btnAdd)
    }

    private fun setupThemeButtons() {
        val currentMode = themeManager.getSavedTheme()
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
        themeManager.applyTheme(mode)
        updateThemeButtons(mode)
    }

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
        configRepository.saveConfigs(configs)
        renderConfig(config)
    }

    private fun renderConfig(config: ConfigItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_config, listContainer, false)
        view.findViewById<TextView>(R.id.tvName).text = config.name
        view.findViewById<TextView>(R.id.tvDate).text = config.lastUpdate?.let { "Обновлено: ${DateUtils.formatDate(it)}" } ?: "Не обновлено"
        view.findViewById<TextView>(R.id.tvRelativeTime).text = config.lastUpdate?.let { DateUtils.getRelativeTime(it) } ?: ""
        view.tag = config
        view.findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { openInWebView(config) }
        view.findViewById<ImageButton>(R.id.btnCopyHtml).setOnClickListener { copyHtmlToClipboard(config) }
        view.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { showConfigMenu(config, view) }
        listContainer.addView(view)
    }

    private fun copyHtmlToClipboard(config: ConfigItem) {
        if (config.rawContent != null) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("raw_html", config.rawContent))
            Toast.makeText(this, "HTML скопирован", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "HTML не сохранен. Сначала обновите конфиг.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInWebView(config: ConfigItem) {
        currentConfig = config
        currentConfigView = listContainer.findViewWithTag<View>(config)

        // 🔥 Авто-прокси: оборачиваем ссылку через Yandex Translate
        var url = config.url.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        val urlToLoad = wrapWithProxy(url)

        webView.loadUrl(urlToLoad)
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
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.d("Wisp", "Ошибка загрузки: ${error?.description}")
                Toast.makeText(this@MainActivity, "Ошибка загрузки страницы", Toast.LENGTH_LONG).show()
            }
            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.d("Wisp", "Ошибка загрузки: $description")
                Toast.makeText(this@MainActivity, "Ошибка загрузки страницы", Toast.LENGTH_LONG).show()
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript("(function() { return document.documentElement.outerHTML; })()") { html ->
                    html?.let { currentConfig?.rawContent = HtmlParser.unescapeHtml(it) }
                }
            }
        }
        webView.visibility = View.GONE
        btnDone.visibility = View.GONE
        btnClose.visibility = View.GONE
        btnDone.setOnClickListener { saveAndCopyPage() }
        btnClose.setOnClickListener { exitWebView() }
    }

    private fun saveAndCopyPage() {
        val content = currentConfig?.rawContent ?: run {
            Toast.makeText(this, "Ошибка: страница не загружена. Проверьте интернет.", Toast.LENGTH_LONG).show()
            return
        }
        val urls = HtmlParser.extractVpnUrls(content)
        if (urls.isNotEmpty()) {
            val result = urls.joinToString("\n")
            currentConfig?.rawContent = result
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("vpn_urls", result))
            currentConfig?.lastUpdate = java.util.Date()
            configRepository.saveConfigs(configs)
            currentConfigView?.findViewById<TextView>(R.id.tvDate)?.text = "Обновлено: ${DateUtils.formatDate(currentConfig!!.lastUpdate!!)}"
            currentConfigView?.findViewById<TextView>(R.id.tvRelativeTime)?.text = DateUtils.getRelativeTime(currentConfig!!.lastUpdate!!)
            exitWebView()
            Toast.makeText(this, "Извлечено ссылок: ${urls.size}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "VPN ссылки не найдены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitWebView() {
        webView.visibility = View.GONE
        btnDone.visibility = View.GONE
        btnClose.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        btnAdd.visibility = View.VISIBLE
    }

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
                    configRepository.saveConfigs(configs)
                    Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
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
                        configRepository.saveConfigs(configs)
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun loadConfigs() {
        val loaded = configRepository.loadConfigs()
        configs.clear()
        listContainer.removeAllViews()
        configs.addAll(loaded)
        configs.forEach { renderConfig(it) }
    }

    // ================= PROXY HELPER =================
    /**
     * Автоматически оборачивает сырую ссылку через Yandex Translate proxy.
     * Если ссылка уже проксирована или не является HTTP(S) — возвращает без изменений.
     */
    private fun wrapWithProxy(rawUrl: String): String {
        if (rawUrl.contains("translate.yandex.ru")) return rawUrl
        if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            return rawUrl
        }
        val encodedUrl = URLEncoder.encode(rawUrl, "UTF-8")
        return "https://translate.yandex.ru/translate?url=$encodedUrl&lang=en-ru"
    }
}