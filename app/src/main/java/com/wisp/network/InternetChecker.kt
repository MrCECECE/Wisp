package com.wisp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class InternetChecker(
    client: OkHttpClient,
    private val probeUrl: String = DEFAULT_PROBE_URL
) {
    private val probeClient: OkHttpClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(probeUrl).head().build()
            probeClient.newCall(request).execute().use { response ->
                response.code == 204
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val DEFAULT_PROBE_URL = "https://cp.cloudflare.com/generate_204"
    }
}