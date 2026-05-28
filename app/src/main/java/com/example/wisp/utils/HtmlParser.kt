package com.example.wisp.utils

object HtmlParser {
    fun unescapeHtml(html: String): String {
        var result = html
        result = result.replace("\\u003C", "<")
        result = result.replace("\\u003E", ">")
        result = result.replace("\\u0022", "\"")
        result = result.replace("\\u0026", "&")
        result = result.replace("\\n", "\n")
        result = result.replace("\\t", "\t")
        result = result.removeSurrounding("\"")
        return result
    }

    fun extractVpnUrls(html: String): List<String> {
        val urls = mutableSetOf<String>()
        val preRegex = Regex("<pre[^>]*>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)

        // ✅ ИСПРАВЛЕНО: findAll вместо find — собираем ссылки из ВСЕХ блоков <pre>
        val preMatches = preRegex.findAll(html)
        if (!preMatches.iterator().hasNext()) return emptyList()

        preMatches.forEach { match ->
            val preContent = match.groupValues[1]
            preContent.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
                    var clean = trimmed
                        .replace("&amp;", "&")
                        .replace("%5C", "")
                        .replace("%22", "")
                        .replace("%2C", ",")
                    clean = Regex("headerType=(none[,%]*)", RegexOption.IGNORE_CASE).replace(clean, "headerType=none")
                    urls.add(clean)
                }
            }
        }
        return urls.toList()
    }
}