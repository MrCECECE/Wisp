# Task 3: Общий HTTP-клиент, проверка интернета и типа сети

## Goal
Создать общий `HttpClient`, `InternetChecker` (пробный запрос к Cloudflare 204), `NetworkMonitor` (определение типа сети). Перевести `ForegroundUpdateManager` на общий клиент. Добавить тесты для `InternetChecker`.

## Files
- Create: `app/src/main/java/com/wisp/network/HttpClient.kt`
- Create: `app/src/main/java/com/wisp/network/InternetChecker.kt`
- Create: `app/src/main/java/com/wisp/network/NetworkMonitor.kt`
- Create: `app/src/test/java/com/wisp/network/InternetCheckerTest.kt`
- Modify: `app/src/main/java/com/wisp/update/ForegroundUpdateManager.kt`
- Modify: `gradle/libs.versions.toml` (add mockwebserver dependency)
- Modify: `app/build.gradle.kts` (add testImplementation)

## Interfaces
- `object HttpClient { val client: OkHttpClient }` — connect 10s, read 30s, один на приложение
- `class InternetChecker(client: OkHttpClient, probeUrl: String = DEFAULT_PROBE_URL) { suspend fun isAvailable(): Boolean }`; `DEFAULT_PROBE_URL = "https://cp.cloudflare.com/generate_204"`
- `enum class NetworkType { WIFI, MOBILE, NONE }`
- `object NetworkMonitor { fun getType(context: Context): NetworkType }`

## Step 1: Add test dependency
In `gradle/libs.versions.toml`:
```toml
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```
In `app/build.gradle.kts` in dependencies:
```kotlin
testImplementation(libs.okhttp.mockwebserver)
```

## Step 2: InternetChecker tests
```kotlin
package com.wisp.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetCheckerTest {

    @Test
    fun `returns true on 204`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val checker = InternetChecker(HttpClient.client, server.url("/generate_204").toString())
            assertTrue(checker.isAvailable())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `returns false on 500`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val checker = InternetChecker(HttpClient.client, server.url("/generate_204").toString())
            assertFalse(checker.isAvailable())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `returns false when server is down`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val url = server.url("/generate_204").toString()
        server.shutdown()

        assertFalse(InternetChecker(HttpClient.client, url).isAvailable())
    }
}
```

## Step 3: Implement HttpClient and InternetChecker
- `HttpClient`: lazy `OkHttpClient.Builder().connectTimeout(10, SECONDS).readTimeout(30, SECONDS).build()`
- `InternetChecker`: constructor creates `probeClient = client.newBuilder().connectTimeout(3, SECONDS).readTimeout(3, SECONDS).callTimeout(5, SECONDS).build()`. `isAvailable()` — `withContext(Dispatchers.IO)`, HEAD request, `response.code == 204`; any exception → `false`

## Step 4: Implement NetworkMonitor
`getType(context)`:
1. Get `activeNetwork` and its `NetworkCapabilities`; no network/capabilities or no `NET_CAPABILITY_INTERNET` → `NONE`
2. `TRANSPORT_WIFI` or `TRANSPORT_ETHERNET` → `WIFI`
3. `TRANSPORT_CELLULAR` → `MOBILE`
4. `TRANSPORT_VPN` → iterate `allNetworks`, skip VPN networks; first with `NET_CAPABILITY_INTERNET` and `TRANSPORT_WIFI` → `WIFI`; otherwise first with `TRANSPORT_CELLULAR` → `MOBILE`
5. Otherwise → `NONE`

## Step 5: Update ForegroundUpdateManager
- Replace `private val client = OkHttpClient.Builder()...` with `private val client = HttpClient.client`
- Remove unused `java.util.concurrent.TimeUnit` imports
- In `onDestroy()`: keep only `cancelDownload()`; **remove** `client.dispatcher.cancelAll()` — otherwise update manager will cancel other requests on the shared client (including subscription fetches)

## Tests to run
`.\gradlew.bat testDebugUnitTest --tests "com.wisp.network.InternetCheckerTest"`

## Global Constraints
- Только Android, один модуль `:app`
- AGP 9.4.0, Gradle 9.8.0
- Новые runtime-зависимости не добавлять (mockwebserver только testImplementation)
- Коммиты в текущую ветку
- Release конфиг не трогать

## Report
Write report to: `C:\Users\serzh\AndroidStudioProjects\Wisp\.superpowers\sdd\2026-10-24-wisp-smart-updates\task-3-report.md`

Report must include:
- Status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Commits made (hashes)
- Test summary (results of running InternetCheckerTest)
- Files changed
- Concerns/issues
- What was done
