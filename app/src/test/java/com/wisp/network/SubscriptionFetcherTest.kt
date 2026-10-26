package com.wisp.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFetcherTest {

    @Test
    fun `normalizeUrl adds https when scheme missing`() {
        assertEquals("https://example.com/sub", SubscriptionFetcher.normalizeUrl("example.com/sub"))
    }

    @Test
    fun `normalizeUrl keeps existing scheme`() {
        assertEquals("http://example.com", SubscriptionFetcher.normalizeUrl("http://example.com"))
        assertEquals("https://example.com", SubscriptionFetcher.normalizeUrl("https://example.com"))
    }

    @Test
    fun `normalizeUrl turns github blob link into raw file link`() {
        assertEquals(
            "https://github.com/owner/repo/blob/main/subs.txt?raw=true",
            SubscriptionFetcher.normalizeUrl("github.com/owner/repo/blob/main/subs.txt")
        )
    }

    @Test
    fun `normalizeUrl keeps existing github query parameters`() {
        assertEquals(
            "https://github.com/owner/repo/blob/main/subs.txt?plain=1&raw=true",
            SubscriptionFetcher.normalizeUrl("https://github.com/owner/repo/blob/main/subs.txt?plain=1")
        )
    }

    @Test
    fun `normalizeUrl leaves raw github link untouched`() {
        val raw = "https://raw.githubusercontent.com/owner/repo/main/subs.txt"
        assertEquals(raw, SubscriptionFetcher.normalizeUrl(raw))
    }

    @Test
    fun `fetchDirect returns body`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("vless://a\ntrojan://b"))
        server.start()
        try {
            val result = SubscriptionFetcher(OkHttpClient()).fetchDirect(server.url("/sub").toString())
            assertTrue(result.isSuccess)
            assertEquals("vless://a\ntrojan://b", result.getOrNull())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `fetchDirect returns failure on 500`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val result = SubscriptionFetcher(OkHttpClient()).fetchDirect(server.url("/sub").toString())
            assertTrue(result.isFailure)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `rejects oversized body`() = runBlocking {
        val server = MockWebServer()
        val oversized = Buffer().write(ByteArray((SubscriptionFetcher.MAX_BODY_BYTES + 1).toInt()))
        server.enqueue(MockResponse().setBody(oversized))
        server.start()
        try {
            val result = SubscriptionFetcher(OkHttpClient()).fetchDirect(server.url("/sub").toString())
            assertTrue(result.isFailure)
        } finally {
            server.shutdown()
        }
    }
}
