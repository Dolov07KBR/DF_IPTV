package com.dolov07kbr.dfiptv07.data

/**
 * Устойчивый парсер M3U/M3U8: понимает #EXTINF с атрибутами
 * (group-title, tvg-logo, tvg-id), #EXTGRP, пропускает мусор и дубликаты.
 */
object M3uParser {

    fun parse(playlistId: String, text: String): List<Channel> {
        val out = ArrayList<Channel>(1024)
        val seen = HashSet<String>()
        var name = ""
        var group: String? = null
        var logo: String? = null
        var tvgId: String? = null
        var extgrp: String? = null
        var pending = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", true) -> {
                    val comma = line.lastIndexOf(',')
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    if (name.isEmpty()) name = "Канал"
                    group = attr(line, "group-title")
                    logo = attr(line, "tvg-logo")?.ifBlank { null }
                    tvgId = attr(line, "tvg-id")?.ifBlank { null }
                    pending = true
                }
                line.startsWith("#EXTGRP:", true) -> {
                    extgrp = line.substringAfter(':', "").trim().ifBlank { null }
                }
                line.startsWith("#") -> {
                    // заголовки/комментарии — игнорируем
                }
                else -> {
                    if (!pending) {
                        // строка-URL без EXTINF: всё равно показываем
                        name = line.substringAfterLast('/').ifBlank { "Канал" }
                    }
                    val url = line
                    if (seen.add(url)) {
                        out += Channel(
                            id = "$playlistId|$url",
                            name = name,
                            url = url,
                            group = group ?: extgrp ?: "Без категории",
                            logo = logo,
                            tvgId = tvgId,
                            playlistId = playlistId,
                        )
                    }
                    pending = false
                    name = ""
                    group = null
                    logo = null
                    tvgId = null
                }
            }
        }
        return out
    }

    private fun attr(line: String, key: String): String? =
        Regex("$key=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
}
