package com.example.wisp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ForegroundUpdateManager(
    private val context: Context,
    private val owner: String,
    private val repo: String,
    private val currentVersionCode: Int
) {
    private companion object {
        private const val CHECK_INTERVAL_HOURS = 0L
        private val CHECK_INTERVAL_MS = CHECK_INTERVAL_HOURS * 60 * 60 * 1000
        private const val KEY_LAST_SUCCESS = "last_success_time"
        private const val KEY_HAS_PENDING = "has_pending_update"
        private const val KEY_TAG = "update_tag"
        private const val KEY_URL = "update_url"
        private const val KEY_CHANGELOG = "update_changelog"
        private const val KEY_PENDING_VERSION_CODE = "pending_version_code"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private var downloadJob: Job? = null

    fun checkForUpdate(onResult: (tag: String, url: String, changelog: String) -> Unit) {
        if (prefs.getBoolean(KEY_HAS_PENDING, false)) {
            val tag = prefs.getString(KEY_TAG, "") ?: ""
            val url = prefs.getString(KEY_URL, "") ?: ""
            val changelog = prefs.getString(KEY_CHANGELOG, "") ?: ""
            val pendingVersionCode = prefs.getInt(KEY_PENDING_VERSION_CODE, 0)
            if (tag.isNotEmpty() && url.isNotEmpty() && pendingVersionCode > currentVersionCode) {
                onResult(tag, url, changelog)
            } else {
                clearPending()
            }
            return
        }

        val lastCheck = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@launch
                }
                val bodyString = response.body?.string() ?: return@launch
                response.close()
                val json = JSONObject(bodyString)
                val tagName = json.optString("tag_name", "")
                val assets = json.optJSONArray("assets")
                if (assets == null || assets.length() == 0) return@launch

                val downloadUrl = assets.getJSONObject(0).optString("browser_download_url")
                val changelog = json.optString("body", "Исправления и улучшения")
                val newVersionCode = parseVersionCode(tagName)

                prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()

                if (newVersionCode > currentVersionCode) {
                    prefs.edit()
                        .putBoolean(KEY_HAS_PENDING, true)
                        .putString(KEY_TAG, tagName)
                        .putString(KEY_URL, downloadUrl)
                        .putString(KEY_CHANGELOG, changelog)
                        .putInt(KEY_PENDING_VERSION_CODE, newVersionCode)
                        .apply()
                    withContext(Dispatchers.Main) {
                        onResult(tagName, downloadUrl, changelog)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseVersionCode(tag: String): Int {
        val clean = tag.trimStart('v', 'V')
        val major = clean.split(".").firstOrNull() ?: return 0
        return major.toIntOrNull() ?: 0
    }

    fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit,
        scope: CoroutineScope
    ) {
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val body = response.body ?: throw Exception("Empty body")
                val contentLength = body.contentLength()
                val file = File(context.filesDir, "update.apk")

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            ensureActive()
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (contentLength > 0) (downloaded * 100 / contentLength).toInt() else -1
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
                response.close()
                withContext(Dispatchers.Main) { onSuccess(file) }
            } catch (e: CancellationException) {
                // Загрузка отменена пользователем
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) { onError(e) }
                }
            }
        }
    }

    fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(intent)
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun clearPending() {
        prefs.edit()
            .putBoolean(KEY_HAS_PENDING, false)
            .remove(KEY_TAG)
            .remove(KEY_URL)
            .remove(KEY_CHANGELOG)
            .remove(KEY_PENDING_VERSION_CODE)
            .apply()
    }

    fun onDestroy() {
        cancelDownload()
        client.dispatcher.cancelAll()
    }
}