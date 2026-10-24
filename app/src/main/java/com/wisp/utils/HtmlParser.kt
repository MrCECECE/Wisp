package com.wisp.utils

import org.json.JSONTokener
import java.util.LinkedHashSet

object HtmlParser {

    private val PROTOCOLS = listOf(
        "vless", "trojan", "vmess", "hysteria", "hysteria2", "hy2", "ss", "ssr", "tuic"
    )

    private val PRE_REGEX = Regex("<pre[^>]*>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)
    private val HEADER_NONE_REGEX = Regex("(?i)headerType=none[,%]*(?=&|$)")

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
        var result = trimmed.removeSurrounding("\"")
        result = result.replace("\\u003C", "<", ignoreCase = true)
        result = result.replace("\\u003E", ">", ignoreCase = true)
        result = result.replace("\\u0022", "\"", ignoreCase = true)
        result = result.replace("\\u0026", "&", ignoreCase = true)
        result = result.replace("\\u0027", "'", ignoreCase = true)
        result = result.replace("\\u003D", "=", ignoreCase = true)
        result = result.replace("\\/", "/")
        result = result.replace("\\n", "\n")
        result = result.replace("\\t", "\t")
        return result
    }

    fun extractVpnUrls(text: String): List<String> {
        val urls = LinkedHashSet<String>()

        val sources = if (text.contains("<pre", ignoreCase = true)) {
            val result = mutableListOf<String>()
            for (match in PRE_REGEX.findAll(text)) {
                result.add(match.groupValues[1])
            }
            result
        } else {
            listOf(text)
        }

        for (source in sources) {
            for (line in source.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val lower = trimmed.lowercase()
                val matchesProtocol = PROTOCOLS.any { protocol ->
                    lower.startsWith("$protocol://")
                }
                if (matchesProtocol) {
                    var clean = trimmed
                        .replace("&amp;", "&")
                        .replace("%5C", "")
                        .replace("%22", "")
                        .replace("%2C", ",")
                    clean = HEADER_NONE_REGEX.replace(clean, "headerType=none")
                    urls.add(clean)
                }
            }
        }
        return urls.toList()
    }
}
