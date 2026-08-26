package com.wisp.utils

import org.json.JSONTokener

object HtmlParser {

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

    fun extractVpnUrls(html: String): List<String> {
        val urls = mutableSetOf<String>()
        val preRegex = Regex("<pre[^>]*>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)

        preMatches@ for (match in preRegex.findAll(html)) {
            val preContent = match.groupValues[1]
            for (line in preContent.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
                    var clean = trimmed
                        .replace("&amp;", "&")
                        .replace("%5C", "")
                        .replace("%22", "")
                        .replace("%2C", ",")
                    // Артефакты прокси ("none,", "none%") чистим только на границе параметра,
                    // чтобы не испортить валидное значение посередине URL
                    clean = Regex("(?i)headerType=none[,%]*(?=&|$)").replace(clean, "headerType=none")
                    urls.add(clean)
                }
            }
        }
        return urls.toList()
    }
}
