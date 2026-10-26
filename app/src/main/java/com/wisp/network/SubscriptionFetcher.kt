package com.wisp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

class SubscriptionFetcher(private val client: OkHttpClient) {

    suspend fun fetchDirect(rawUrl: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = normalizeUrl(rawUrl)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }

                val body = response.body ?: run {
                    response.close()
                    return@withContext Result.failure(IOException("Empty body"))
                }

                val contentLength = body.contentLength()
                if (contentLength > MAX_BODY_BYTES) {
                    response.close()
                    return@withContext Result.failure(IOException("Body too large"))
                }

                val inputStream = body.byteStream()
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var totalRead = 0L
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    totalRead += read
                    if (totalRead > MAX_BODY_BYTES) {
                        response.close()
                        return@withContext Result.failure(IOException("Body too large"))
                    }
                    outputStream.write(buffer, 0, read)
                }
                response.close()

                Result.success(outputStream.toString("UTF-8"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        const val MAX_BODY_BYTES: Long = 5 * 1024 * 1024

        /**
         * Ссылка на файл в веб-интерфейсе GitHub отдаёт HTML-страницу,
         * а не сам файл. Параметр raw=true перенаправляет на raw.githubusercontent.com,
         * где лежит обычный текст, который парсится без HTML.
         */
        private val GITHUB_BLOB_LINK =
            Regex("(?i)^https?://(?:www\\.)?github\\.com/[^/]+/[^/]+/blob/.+$")

        fun normalizeUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            val withScheme = if (
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
            if (!GITHUB_BLOB_LINK.matches(withScheme)) return withScheme
            val separator = if (withScheme.contains('?')) "&" else "?"
            return "$withScheme${separator}raw=true"
        }
    }
}
