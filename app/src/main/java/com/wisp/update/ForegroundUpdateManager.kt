package com.wisp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ForegroundUpdateManager(
    private val context: Context,
    private val owner: String,
    private val repo: String,
    currentVersionName: String
) {
    /** Текущая версия в виде сравнимого числа: major*1_000_000 + minor*1_000 + patch */
    private val currentVersion: Long = parseVersion(currentVersionName)

    private companion object {
        private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val KEY_LAST_SUCCESS = "last_success_time"
        private const val KEY_HAS_PENDING = "has_pending_update"
        private const val KEY_TAG = "update_tag"
        private const val KEY_URL = "update_url"
        private const val KEY_CHANGELOG = "update_changelog"

        // Новый ключ с Long-значением. Старый ключ "pending_version_code" хранил Int —
        // чтение Long под тем же именем вызвало бы ClassCastException у обновившихся пользователей.
        private const val KEY_PENDING_VERSION = "pending_version_long"
        private const val LEGACY_KEY_PENDING_VERSION_CODE = "pending_version_code"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private var downloadJob: Job? = null

    fun checkForUpdate(
        scope: CoroutineScope,
        onResult: (tag: String, url: String, changelog: String) -> Unit
    ) {
        if (prefs.getBoolean(KEY_HAS_PENDING, false)) {
            val tag = prefs.getString(KEY_TAG, "") ?: ""
            val url = prefs.getString(KEY_URL, "") ?: ""
            val changelog = prefs.getString(KEY_CHANGELOG, "") ?: ""
            val pendingVersion = prefs.getLong(KEY_PENDING_VERSION, 0L)
            if (tag.isNotEmpty() && url.isNotEmpty() && pendingVersion > currentVersion) {
                onResult(tag, url, changelog)
                return
            }
            // Невалидный pending — чистим и всё равно проверяем сеть ниже
            clearPending()
        }

        val lastCheck = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return

        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@launch
                    val bodyString = response.body?.string() ?: return@launch
                    val json = JSONObject(bodyString)
                    val tagName = json.optString("tag_name", "")
                    val downloadUrl = findApkAsset(json.optJSONArray("assets"))
                    if (tagName.isEmpty() || downloadUrl == null) return@launch

                    val changelog = json.optString("body", "").ifBlank { "Исправления и улучшения" }
                    val newVersion = parseVersion(tagName)

                    prefs.edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply()

                    if (newVersion > currentVersion) {
                        prefs.edit()
                            .putBoolean(KEY_HAS_PENDING, true)
                            .putString(KEY_TAG, tagName)
                            .putString(KEY_URL, downloadUrl)
                            .putString(KEY_CHANGELOG, changelog)
                            .putLong(KEY_PENDING_VERSION, newVersion)
                            .apply()
                        withContext(Dispatchers.Main) {
                            onResult(tagName, downloadUrl, changelog)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Возвращает URL первого asset'а с расширением .apk либо null */
    private fun findApkAsset(assets: org.json.JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) return url
        }
        return null
    }

    /**
     * "v4.2.1" -> 4_002_001. Поддерживает теги с суффиксами (v4.0-beta).
     * Сравнивается с текущей версией, вычисленной из versionName.
     */
    private fun parseVersion(version: String): Long {
        val clean = version.trim().trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val parts = clean.split('.')
        fun part(i: Int): Long = parts.getOrNull(i)?.trim()?.toLongOrNull()?.coerceIn(0, 999) ?: 0L
        return part(0) * 1_000_000L + part(1) * 1_000L + part(2)
    }

    fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit,
        scope: CoroutineScope
    ) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val body = response.body ?: throw Exception("Empty body")
                    val contentLength = body.contentLength()
                    val file = File(context.filesDir, "update.apk")

                    FileOutputStream(file).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var lastPercent = Int.MIN_VALUE
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, read)
                                downloaded += read
                                val percent =
                                    if (contentLength > 0) (downloaded * 100 / contentLength).toInt() else -1
                                // Колбэк в UI только когда процент реально изменился
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) { onProgress(percent) }
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { onSuccess(file) }
                }
            } catch (e: CancellationException) {
                // Загрузка отменена; диалог прогресса закрывает вызывающая сторона
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
    }

    /**
     * @return true, если интент установки запущен; false, если требуется разрешение
     * "установка из неизвестных источников" (открыт экран настроек)
     */
    fun installApk(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return true
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /**
     * Удаляет APK прошлой загрузки. Безопасно вызывать только при старте приложения,
     * когда установщик гарантированно не держит файл открытым.
     */
    fun cleanupOldApk() {
        File(context.filesDir, "update.apk").delete()
    }

    fun clearPending() {
        prefs.edit()
            .putBoolean(KEY_HAS_PENDING, false)
            .remove(KEY_TAG)
            .remove(KEY_URL)
            .remove(KEY_CHANGELOG)
            .remove(KEY_PENDING_VERSION)
            .remove(LEGACY_KEY_PENDING_VERSION_CODE)
            .apply()
    }

    fun onDestroy() {
        cancelDownload()
        client.dispatcher.cancelAll()
    }
}
