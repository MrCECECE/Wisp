package com.wisp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {

    @Test
    fun `extracts plain text subscription`() {
        val text = """
            vless://uuid@example.com:443?type=tcp#Node1
            trojan://pass@example.com:443#Node2
        """.trimIndent()

        val urls = HtmlParser.extractVpnUrls(text)

        assertEquals(2, urls.size)
        assertTrue(urls.contains("vless://uuid@example.com:443?type=tcp#Node1"))
        assertTrue(urls.contains("trojan://pass@example.com:443#Node2"))
    }

    @Test
    fun `extracts all supported protocols`() {
        val text = listOf(
            "vless://a", "trojan://b", "vmess://c", "hysteria://d",
            "hysteria2://e", "hy2://f", "ss://g", "ssr://h", "tuic://i"
        ).joinToString("\n")

        assertEquals(9, HtmlParser.extractVpnUrls(text).size)
    }

    @Test
    fun `matches protocol case insensitively`() {
        val urls = HtmlParser.extractVpnUrls("VLESS://a\nTrojan://b")
        assertEquals(2, urls.size)
    }

    @Test
    fun `deduplicates repeated urls`() {
        val urls = HtmlParser.extractVpnUrls("vless://a\nvless://a\nvless://a")
        assertEquals(listOf("vless://a"), urls)
    }

    @Test
    fun `extracts from pre block`() {
        val html = "<html><body><pre>vless://a\ntrojan://b</pre></body></html>"
        assertEquals(2, HtmlParser.extractVpnUrls(html).size)
    }

    @Test
    fun `cleans html artifacts`() {
        val urls = HtmlParser.extractVpnUrls(
            "vless://a?x=1&amp;y=2&headerType=none,\nvless://b%5C%22c"
        )
        assertEquals(
            listOf("vless://a?x=1&y=2&headerType=none", "vless://bc"),
            urls
        )
    }

    @Test
    fun `ignores non protocol lines`() {
        val urls = HtmlParser.extractVpnUrls("hello\nhttps://example.com\n")
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `extracts from github blob html`() {
        val html = """
            <!DOCTYPE html><html><body>
            <div class="react-file-line html-div"><span class="pl-s">vless://a@host:443?type=tcp&amp;security=reality#Node</span></div>
            <div class="react-file-line html-div"><span>trojan://pass@host:443#T</span></div>
            </body></html>
        """.trimIndent()

        val urls = HtmlParser.extractVpnUrls(html)

        assertEquals(
            listOf("vless://a@host:443?type=tcp&security=reality#Node", "trojan://pass@host:443#T"),
            urls
        )
    }

    @Test
    fun `extracts from json payload with unicode escapes`() {
        val html = "{\"lines\":[\"vless://a@host:443?pbk=1\\u0026sid=2#N\"]}"

        assertEquals(listOf("vless://a@host:443?pbk=1&sid=2#N"), HtmlParser.extractVpnUrls(html))
    }

    @Test
    fun `finds urls not at line start`() {
        val text = "- vless://a@host:443#A\nСкачать: trojan://b@host:443#B"

        assertEquals(
            listOf("vless://a@host:443#A", "trojan://b@host:443#B"),
            HtmlParser.extractVpnUrls(text)
        )
    }

    @Test
    fun `keeps vmess base64 padding`() {
        val urls = HtmlParser.extractVpnUrls("vmess://eyJ2IjoiMiJ9==")
        assertEquals(listOf("vmess://eyJ2IjoiMiJ9=="), urls)
    }
}
