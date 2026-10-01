package com.dolov07kbr.dfiptv07.data

import android.util.Xml
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

/**
 * Необязательный EPG в формате XMLTV: URL задаётся в настройках.
 * Сопоставление с каналом — по tvg-id, иначе по нормализованному имени.
 */
object EpgRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class EpgState(val loading: Boolean = false, val ready: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(EpgState())
    val state: StateFlow<EpgState> = _state.asStateFlow()

    /** key -> отсортированные программы */
    private var programmesByKey: Map<String, List<Programme>> = emptyMap()
    /** channel-id (XMLTV) -> ключи сопоставления */
    private var aliasByKey: Map<String, Set<String>> = emptyMap()

    fun refreshIfConfigured(force: Boolean = false) {
        val url = Df.store.epgUrl.trim()
        if (url.isEmpty()) return
        scope.launch { load(url) }
    }

    private fun load(url: String) {
        _state.value = EpgState(loading = true)
        try {
            val text = httpGet(url)
            parse(text)
            _state.value = EpgState(ready = true)
        } catch (e: Exception) {
            _state.value = EpgState(error = "EPG: ${e.message?.take(50)}")
        }
    }

    /** Сейчас / следующая передача для канала. */
    fun nowNext(channel: Channel): Pair<Programme?, Programme?>? {
        if (programmesByKey.isEmpty()) return null
        val keys = buildSet {
            channel.tvgId?.let { add(norm(it)) }
            add(norm(channel.name))
            aliasByKey[norm(channel.tvgId ?: "")]?.let { addAll(it) }
        }
        val list = keys.firstNotNullOfOrNull { k -> programmesByKey[k] } ?: return null
        val now = System.currentTimeMillis()
        val idx = list.indexOfFirst { it.stop > now }
        val current = if (idx >= 0 && list[idx].start <= now) list[idx] else null
        val next = if (idx >= 0 && current != null && idx + 1 < list.size) list[idx + 1]
                   else if (current == null && idx >= 0) list[idx] else null
        return current to next
    }

    private fun norm(s: String): String =
        s.lowercase(Locale.ROOT).replace(Regex("[^a-zа-яё0-9]+"), " ").trim()

    private fun parse(text: String) {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(text.reader())
        val programmes = HashMap<String, MutableList<Programme>>()
        val names = HashMap<String, MutableSet<String>>()

        var event = parser.eventType
        var inChannel = false
        var inProgramme = false
        var channelId: String? = null
        var start = 0L
        var stop = 0L
        var title: String? = null
        var textBuf: StringBuilder? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> { inChannel = true; channelId = parser.attr("id") }
                    "programme" -> {
                        inProgramme = true
                        channelId = parser.attr("channel")
                        start = parseTime(parser.attr("start"))
                        stop = parseTime(parser.attr("stop"))
                        title = null
                    }
                    "display-name" -> if (inChannel) textBuf = StringBuilder()
                    "title" -> if (inProgramme) textBuf = StringBuilder()
                }
                XmlPullParser.TEXT -> textBuf?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "channel" -> inChannel = false
                    "programme" -> {
                        val ch = channelId
                        if (ch != null && start > 0 && stop > start) {
                            programmes.getOrPut(norm(ch)) { mutableListOf() }
                                .add(Programme(start, stop, title?.trim() ?: "Передача"))
                        }
                        inProgramme = false
                    }
                    "display-name" -> {
                        val v = textBuf?.toString()?.trim().orEmpty()
                        val ch = channelId
                        if (inChannel && ch != null && v.isNotEmpty()) {
                            names.getOrPut(norm(ch)) { mutableSetOf() }.add(norm(v))
                        }
                        textBuf = null
                    }
                    "title" -> { title = textBuf?.toString(); textBuf = null }
                }
            }
            event = parser.next()
        }

        programmes.values.forEach { it.sortBy { p -> p.start } }
        programmesByKey = programmes
        aliasByKey = names
    }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching {
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(value)?.time ?: 0L
        }.recoverCatching {
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(value)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { return it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
