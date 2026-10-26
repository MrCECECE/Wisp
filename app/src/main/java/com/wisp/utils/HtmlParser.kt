package com.wisp.utils

import org.json.JSONTokener
import java.util.LinkedHashSet

object HtmlParser {

    private val PROTOCOLS = listOf(
        "vless", "trojan", "vmess", "hysteria2", "hysteria", "hy2", "ssr", "ss", "tuic"
    )

    /**
     * Ссылка распознаётся в любом месте текста, а не только в начале строки:
     * в HTML-страницах (например, на GitHub) ссылка стоит после тегов или
     * маркера списка, а в JSON-полезной нагрузке — внутри строки.
     */
    private val URL_REGEX = Regex(
        "(?i)(?:" + PROTOCOLS.joinToString("|") + ")://[^\\s\"'<>\\\\]+"
    )

    /**
     * Теги, которые в HTML разделяют строки. GitHub кладёт каждую строку файла
     * в отдельный <div>, поэтому без замены на перевод строки строки склеятся.
     */
    private val BLOCK_TAG_REGEX = Regex(
        "(?i)<(?:br|/p|/div|/li|/tr|/pre|/h[1-6]|/table|/section|/article|/td|/th)\\s*/?>"
    )
    private val ANY_TAG_REGEX = Regex("<[^>]+>")

    /** Мусор, который может прилипнуть к ссылке в тексте или markdown. */
    private val TRAILING_JUNK = charArrayOf(',', '.', ';', ':', ')', ']', '}', '\'', '"')

    private val HEADER_NONE_REGEX = Regex("(?i)headerType=none[,%]*(?=&|$)")

    private val ESCAPES = listOf(
        "\\u003C" to "<",
        "\\u003E" to ">",
        "\\u0022" to "\"",
        "\\u0026" to "&",
        "\\u0027" to "'",
        "\\u003D" to "=",
        "\\/" to "/",
        "\\n" to "\n",
        "\\t" to "\t"
    )

    /**
     * evaluateJavascript возвращает результат в виде JSON-литерала.
     * Если строка обёрнута в кавычки — декодируем её штатным JSON-парсером
     * (это покрывает \u003C, \u0022, \n, \t, \" и любые другие эскейпы).
     * Иначе применяем ручные замены как fallback.
     */
    fun unescapeHtml(html: String): String {
        val trimmed = html.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                val decoded = JSONTokener(trimmed).nextValue()
                if (decoded is String) return decoded
            } catch (_: Exception) {
                // не валидный JSON — падаем в ручные замены
            }
        }
        return decodeEscapes(trimmed.removeSurrounding("\""))
    }

    private fun decodeEscapes(value: String): String {
        var result = value
        for ((from, to) in ESCAPES) {
            result = result.replace(from, to, ignoreCase = true)
        }
        return result
    }

    /**
     * Приводит входной текст к виду «одна строка — один элемент»:
     * снимает HTML-теги, раскрывает сущности (&amp;, &lt;, ...) и
     * \uXXXX-эскейпы из JSON-полезной нагрузки страницы.
     */
    private fun normalizeContent(raw: String): String {
        var text = BLOCK_TAG_REGEX.replace(raw, "\n")
        text = ANY_TAG_REGEX.replace(text, "")
        text = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        return decodeEscapes(text)
    }

    fun extractVpnUrls(text: String): List<String> {
        val urls = LinkedHashSet<String>()
        for (match in URL_REGEX.findAll(normalizeContent(text))) {
            var clean = match.value.trimEnd(*TRAILING_JUNK)
            clean = clean
                .replace("&amp;", "&")
                .replace("%5C", "")
                .replace("%22", "")
                .replace("%2C", ",")
            clean = HEADER_NONE_REGEX.replace(clean, "headerType=none")
            urls.add(clean)
        }
        return urls.toList()
    }
}
