package com.wisp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.wisp.model.ConfigItem
import com.wisp.network.HttpClient
import com.wisp.network.InternetChecker
import com.wisp.network.SubscriptionFetcher
import com.wisp.repository.ConfigRepository
import com.wisp.update.AutoRefreshPolicy
import com.wisp.update.ForegroundUpdateManager
import com.wisp.utils.DateUtils
import com.wisp.utils.HtmlParser
import com.wisp.utils.ThemeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var btnDone: Button
    private lateinit var btnClose: Button
    private lateinit var btnThemeLight: Button
    private lateinit var btnThemeSystem: Button
    private lateinit var btnThemeDark: Button
    private lateinit var listContainer: LinearLayout
    private lateinit var btnAdd: Button
    private lateinit var webViewButtons: LinearLayout
    private lateinit var btnSettings: Button
    private lateinit var bannerOldVersion: TextView

    companion object {
        /** Пакеты предыдущих версий приложения (до переименования пакета) */
        private val LEGACY_PACKAGES = arrayOf("com.example.wisp", "com.example.wisp.debug")

        /**
         * Забирает текст страницы из WebView. Кроме <pre>/<code>/<textarea>
         * (туда Chrome кладёт text/plain) используется body.innerText —
         * он покрывает HTML-страницы, отрисованные скриптами.
         */
        private const val EXTRACT_PAGE_JS =
            "(function(){var p=[];document.querySelectorAll('pre,code,textarea').forEach(function(e){p.push(e.value!==undefined?e.value:e.textContent);});if(p.length===0&&document.body){p.push(document.body.innerText);}return p.join('\\n');})()"
    }

    private lateinit var updateManager: ForegroundUpdateManager
    private val themeManager by lazy { ThemeManager(this) }
    private val configRepository by lazy { ConfigRepository(this) }
    private val configs = mutableListOf<ConfigItem>()
    private var currentConfig: ConfigItem? = null
    private var currentConfigView: View? = null
    private var downloadDialog: AlertDialog? = null
    private val internetChecker by lazy { InternetChecker(HttpClient.client) }
    private val subscriptionFetcher by lazy { SubscriptionFetcher(HttpClient.client) }
    private var autoRefreshJob: Job? = null
    private var autoRefreshLoop: Job? = null
    private var isForeground = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val webViewBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitWebView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager.applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupThemeButtons()
        setupAddButton()
        setupSettingsButton()
        checkLegacyInstall()
        setupWebView()
        onBackPressedDispatcher.addCallback(this, webViewBackCallback)

        updateManager = ForegroundUpdateManager(
            context = this,
            owner = "MrCECECE",
            repo = "Wisp",
            currentVersionName = BuildConfig.VERSION_NAME
        )

        // APK прошлой установки мог остаться — чистим до новой проверки обновлений
        updateManager.cleanupOldApk()

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
        webViewButtons = findViewById(R.id.webViewButtons)
        btnSettings = findViewById(R.id.btnSettings)
        bannerOldVersion = findViewById(R.id.bannerOldVersion)
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

    private fun setupSettingsButton() {
        btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * Показывает баннер, если на устройстве ещё стоит старая версия.
     * Проверяются оба варианта старого пакета: release (com.example.wisp)
     * и debug (com.example.wisp.debug) — у debug-сборок был суффикс .debug
     */
    private fun checkLegacyInstall() {
        val legacyInstalled = LEGACY_PACKAGES.any { pkg ->
            try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (legacyInstalled) bannerOldVersion.visibility = View.VISIBLE
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
                    if (name.isEmpty()) name = generateConfigName()
                    addConfig(name, url)
                } else Toast.makeText(this, "Введите ссылку", Toast.LENGTH_SHORT).show()
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

    private fun generateConfigName(): String {
        var n = configs.size + 1
        while (configs.any { it.name == "Конфигурация #$n" }) n++
        return "Конфигурация #$n"
    }

    private fun renderConfig(config: ConfigItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_config, listContainer, false)
        view.findViewById<TextView>(R.id.tvName).text = config.name
        view.findViewById<TextView>(R.id.tvDate).text = config.lastUpdate?.let { "Обновлено: ${DateUtils.formatDate(it)}" } ?: "Не обновлено"
        view.findViewById<TextView>(R.id.tvRelativeTime).text = config.lastUpdate?.let { DateUtils.getRelativeTime(it) } ?: ""
        view.tag = config
        view.findViewById<ImageButton>(R.id.btnRefreshDirect).setOnClickListener { openInWebView(config, view, false) }
        view.findViewById<ImageButton>(R.id.btnRefreshProxy).setOnClickListener { openInWebView(config, view, true) }
        view.findViewById<ImageButton>(R.id.btnCopyUrls).setOnClickListener { copyUrlsToClipboard(config) }
        view.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { showConfigMenu(config, view) }
        listContainer.addView(view)
    }

    private fun copyUrlsToClipboard(config: ConfigItem) {
        if (config.rawContent != null) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("vpn_urls", config.rawContent))
            Toast.makeText(this, "Ссылки скопированы", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ссылки не найдены. Сначала обновите конфиг.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildUrl(rawUrl: String, useProxy: Boolean): String {
        val normalized = SubscriptionFetcher.normalizeUrl(rawUrl)
        if (!useProxy) {
            return normalized
        }
        if (normalized.contains("translate.yandex.ru")) return normalized
        val encodedUrl = Uri.encode(normalized)
        return "https://translate.yandex.ru/translate?url=$encodedUrl&lang=en-ru"
    }

    private fun openInWebView(config: ConfigItem, rowView: View, useProxy: Boolean) {
        currentConfig = config
        currentConfigView = rowView
        val url = buildUrl(config.url, useProxy)
        webView.loadUrl(url)
        webView.visibility = View.VISIBLE
        webViewButtons.visibility = View.VISIBLE
        listContainer.visibility = View.GONE
        btnAdd.visibility = View.GONE
        webViewBackCallback.isEnabled = true
    }

    private fun setupWebView() {
        // 🔒 БЕЗОПАСНОСТЬ: Отключаем доступ к файловой системе и контенту
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        // ⚡ ПРОИЗВОДИТЕЛЬНОСТЬ
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.d("Wisp", "WebView Error: ${error?.description}")
                // Сообщаем об ошибке только если не загрузился главный фрейм,
                // а не отдельные ресурсы (иконки, картинки и т.п.)
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@MainActivity, "Ошибка загрузки страницы", Toast.LENGTH_LONG).show()
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Фиксируем конфиг на момент загрузки: колбэк может прийти позже,
                // чем пользователь переключится на другой конфиг
                val loadedConfig = currentConfig ?: return
                view?.evaluateJavascript(EXTRACT_PAGE_JS) { html ->
                    if (currentConfig !== loadedConfig) return@evaluateJavascript
                    val content = HtmlParser.unescapeHtml(html ?: "")
                    // Для text/plain-страниц WebView не исполняет JS и возвращает null,
                    // поэтому пустой результат просто игнорируем — текст скачается при сохранении.
                    if (HtmlParser.extractVpnUrls(content).isNotEmpty()) {
                        loadedConfig.rawContent = content
                    }
                }
            }
        }

        btnDone.setOnClickListener { saveAndCopyPage() }
        btnClose.setOnClickListener { exitWebView() }
    }

    private fun saveAndCopyPage() {
        val config = currentConfig ?: run {
            Toast.makeText(this, "Ошибка: страница не загружена. Проверьте интернет.", Toast.LENGTH_LONG).show()
            return
        }
        btnDone.isEnabled = false
        // Сначала пробуем DOM: для обычных HTML-страниц (в том числе через Яндекс) этого достаточно.
        webView.evaluateJavascript(EXTRACT_PAGE_JS) { html ->
            if (currentConfig !== config) {
                btnDone.isEnabled = true
                return@evaluateJavascript
            }
            val fromPage = HtmlParser.unescapeHtml(html ?: "")
            if (HtmlParser.extractVpnUrls(fromPage).isNotEmpty()) {
                btnDone.isEnabled = true
                savePageUrls(config, fromPage)
            } else {
                // text/plain-страницы WebView показывает встроенным просмотрщиком:
                // JS там запрещён, evaluateJavascript возвращает null, DOM пуст.
                // Поэтому тот же адрес скачиваем напрямую и парсим как обычный текст.
                fetchAndSave(config, webView.url)
            }
        }
    }

    private fun fetchAndSave(config: ConfigItem, pageUrl: String?) {
        lifecycleScope.launch {
            // Сначала исходная ссылка конфига: она отдаёт файл напрямую.
            // Прокси-страница Яндекса (turbopages) не принимает запросы
            // от не-браузера (403) и годится только как последний вариант.
            val candidates = listOfNotNull(
                config.url,
                pageUrl?.takeIf { it.startsWith("http", ignoreCase = true) && it != config.url }
            )
            for (target in candidates) {
                val body = subscriptionFetcher.fetchDirect(target).getOrNull() ?: continue
                val urls = HtmlParser.extractVpnUrls(body)
                if (urls.isNotEmpty()) {
                    btnDone.isEnabled = true
                    savePageUrls(config, body)
                    return@launch
                }
            }
            btnDone.isEnabled = true
            if (currentConfig === config) {
                Toast.makeText(this@MainActivity, "VPN ссылки не найдены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePageUrls(config: ConfigItem, freshContent: String) {
        var content = freshContent
        var urls = HtmlParser.extractVpnUrls(content)
        if (urls.isEmpty()) {
            content = config.rawContent ?: ""
            urls = HtmlParser.extractVpnUrls(content)
        }
        if (urls.isEmpty()) {
            Toast.makeText(this, "VPN ссылки не найдены", Toast.LENGTH_SHORT).show()
            return
        }

        val result = urls.joinToString("\n")
        config.rawContent = result
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("vpn_urls", result))
        val now = java.util.Date()
        config.lastUpdate = now
        configRepository.saveConfigs(configs)
        currentConfigView?.findViewById<TextView>(R.id.tvDate)?.text = "Обновлено: ${DateUtils.formatDate(now)}"
        currentConfigView?.findViewById<TextView>(R.id.tvRelativeTime)?.text = DateUtils.getRelativeTime(now)
        exitWebView()
        Toast.makeText(this, "Извлечено ссылок: ${urls.size}", Toast.LENGTH_SHORT).show()
    }

    private fun exitWebView() {
        webView.stopLoading()
        webView.visibility = View.GONE
        webViewButtons.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        btnAdd.visibility = View.VISIBLE
        webViewBackCallback.isEnabled = false
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



    private fun showUpdateDialog(tag: String, url: String, changelog: String) {
        // ✅ Диалог не показывается, если активность уходит в фон или уничтожается
        if (isFinishing || isDestroyed) return

        val message = if (changelog.isNotBlank()) changelog else "Исправления и улучшения"
        AlertDialog.Builder(this)
            .setTitle("Доступно обновление: $tag")
            .setMessage(message)
            .setPositiveButton("Обновить") { _, _ ->
                val progressText = TextView(this).apply {
                    text = "Загрузка..."
                    setPadding(48, 32, 48, 16)
                }
                val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(48, 0, 48, 32) }
                }
                val progressLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(progressText)
                    addView(progressBar)
                }
                val downloadDialog = AlertDialog.Builder(this)
                    .setView(progressLayout)
                    .setCancelable(false)
                    .create()
                this@MainActivity.downloadDialog = downloadDialog
                downloadDialog.show()

                updateManager.downloadApk(
                    url = url,
                    onProgress = { percent ->
                        progressText.text = if (percent >= 0) "Загрузка: $percent%" else "Загрузка..."
                    },
                    onSuccess = { file ->
                        if (isFinishing || isDestroyed) return@downloadApk
                        downloadDialog.dismiss()
                        this@MainActivity.downloadDialog = null
                        AlertDialog.Builder(this)
                            .setTitle("Загрузка завершена")
                            .setMessage("Нажмите «Установить» для обновления.")
                            .setPositiveButton("Установить") { _, _ ->
                                // Pending чистим только если установка реально запущена;
                                // при запросе разрешения она остаётся отложенной
                                if (updateManager.installApk(file)) {
                                    updateManager.clearPending()
                                    // Файл НЕ удаляем: установщик читает его асинхронно через
                                    // FileProvider, удаление здесь — гонка и «Не удалось разобрать
                                    // пакет». APK удалится при следующем запуске (cleanupOldApk)
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Разрешите установку и нажмите «Обновить» снова",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .setNegativeButton("Отмена") { _, _ -> file.delete() }
                            .show()
                    },
                    onError = { e ->
                        if (isFinishing || isDestroyed) return@downloadApk
                        downloadDialog.dismiss()
                        this@MainActivity.downloadDialog = null
                        Toast.makeText(this, "Ошибка: ${e.message}. Попробуйте позже или скачайте вручную с GitHub", Toast.LENGTH_LONG).show()
                    },
                    scope = lifecycleScope
                )
            }
            .setNegativeButton("Позже") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onStart() {
        super.onStart()
        isForeground = true
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { scheduleAutoRefresh(1500L) }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                runOnUiThread { scheduleAutoRefresh(1500L) }
            }
        }
        networkCallback = callback
        cm.registerDefaultNetworkCallback(callback)
        startAutoRefreshLoop()
        // При открытии приложения сразу проверяем, не прошло ли 15 минут
        scheduleAutoRefresh()
    }

    override fun onStop() {
        isForeground = false
        // Таймер автообновления НЕ отменяем: подписки обновляются и когда
        // приложение просто висит в списке недавних. Он живёт, пока жив процесс,
        // и умирает вместе с активностью, когда приложение закрывают полностью.
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        networkCallback = null
        super.onStop()
    }

    private fun scheduleAutoRefresh(delayMs: Long = 0L) {
        autoRefreshJob?.cancel()
        autoRefreshJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            runAutoRefresh()
        }
    }

    /** Раз в 15 минут, пока приложение живо — на экране или в списке недавних. */
    private fun startAutoRefreshLoop() {
        if (autoRefreshLoop?.isActive == true) return
        autoRefreshLoop = lifecycleScope.launch {
            while (isActive) {
                delay(AutoRefreshPolicy.MIN_INTERVAL_MS)
                runAutoRefresh()
            }
        }
    }

    private suspend fun runAutoRefresh() {
        // Обновляем при любом интернете — Wi-Fi или мобильном.
        // Но сначала проверяем ссылкой, что сеть реально работает:
        // «сеть есть» и «интернет доступен» — не одно и то же.
        if (!internetChecker.isAvailable()) {
            Log.d("WispAuto", "skip: probe failed")
            return
        }
        if (isForeground) {
            updateManager.checkForUpdate(lifecycleScope) { tag, url, changelog ->
                showUpdateDialog(tag, url, changelog)
            }
        }
        if (!AutoRefreshPolicy.isDue(configRepository.getLastAutoRefresh(), System.currentTimeMillis())) {
            return
        }
        autoRefreshAllConfigs()
        configRepository.setLastAutoRefresh(System.currentTimeMillis())
    }

    private suspend fun autoRefreshAllConfigs() {
        val snapshot = configs.toList()
        var updated = 0
        for (config in snapshot) {
            if (config === currentConfig) continue
            val result = subscriptionFetcher.fetchDirect(config.url)
            result.onSuccess { text ->
                if (!configs.contains(config)) return@onSuccess
                val urls = HtmlParser.extractVpnUrls(text)
                if (urls.isNotEmpty()) {
                    config.rawContent = urls.joinToString("\n")
                    config.lastUpdate = java.util.Date()
                    updated++
                }
            }.onFailure { e ->
                Log.d("WispAuto", "fetch failed: ${config.name}: ${e.message}")
            }
        }
        if (updated > 0) {
            configRepository.saveConfigs(configs)
            loadConfigs()
        }
        Log.d("WispAuto", "updated $updated of ${snapshot.size}")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Отменяем загрузку и закрываем диалог прогресса,
        // иначе он останется висеть в состоянии "Загрузка..."
        updateManager.cancelDownload()
        downloadDialog?.takeIf { it.isShowing }?.dismiss()
        downloadDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.onDestroy()
    }
}