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