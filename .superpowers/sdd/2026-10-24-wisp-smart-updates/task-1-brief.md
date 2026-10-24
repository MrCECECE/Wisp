# Task 1: Мультипротокольный парсер `HtmlParser`

## Goal
Расширить `HtmlParser` для поддержки всех распространённых протоколов и парсинга как из HTML-блоков `<pre>`, так и из сырого текста. Добавить unit-тесты.

## Files
- Modify: `app/src/main/java/com/wisp/utils/HtmlParser.kt`
- Create: `app/src/test/java/com/wisp/utils/HtmlParserTest.kt`

## Interfaces
- `HtmlParser.extractVpnUrls(text: String): List<String>` — та же сигнатура; теперь ищет протоколы и в сыром тексте, и в блоках `<pre>`, дедуплицирует, чистит артефакты
- `HtmlParser.unescapeHtml(html: String): String` — без изменений

## Protocols to support
vless, trojan, vmess, hysteria, hysteria2, hy2, ss, ssr, tuic (case insensitive)

## Tests to write (all must pass)
```kotlin
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
}
```

## Implementation details
- Add private list `PROTOCOLS` with all 9 prefixes
- Add precompiled regexes: `PRE_REGEX` for `<pre[^>]*>(.*?)</pre>` with DOT_MATCHES_ALL, `HEADER_NONE_REGEX` for `(?i)headerType=none[,%]*(?=&|$)`
- If text contains `<pre` (case insensitive) — sources = content of all `<pre>` blocks; else source = entire text
- For each line: trim(), check if starts with any protocol case-insensitively, if match add to LinkedHashSet after cleaning
- Cleaning: replace `&amp;` → `&`, remove `%5C` and `%22`, replace `%2C` → `,`, then apply `HEADER_NONE_REGEX`
- Return `urls.toList()`

## Global Constraints
- Только Android, один модуль `:app`
- AGP 9.4.0, Gradle 9.8.0
- Новые runtime-зависимости не добавлять
- Коммиты в текущую ветку
- Release конфиг не трогать

## Report
Write report to: `C:\Users\serzh\AndroidStudioProjects\Wisp\.superpowers\sdd\2026-10-24-wisp-smart-updates\task-1-report.md`

Report must include:
- Status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Commits made (hashes)
- Test summary (results of running HtmlParserTest)
- Files changed
- Concerns/issues
- What was done
