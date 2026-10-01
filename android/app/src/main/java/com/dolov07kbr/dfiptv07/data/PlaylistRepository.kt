package com.dolov07kbr.dfiptv07.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Загрузка плейлистов: 6 встроенных из assets + пользовательские по URL.
 * Всё живёт в памяти (StateFlow), UI подписывается.
 */
object PlaylistRepository {

    data class RepoState(
        val loading: Boolean = true,
        val playlists: List<Playlist> = emptyList(),
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = _state.asStateFlow()

    private var appContext: Context? = null

    private val builtIn = listOf(
        "DF_IPTV.m3u" to "DF IPTV — основной",
        "07.m3u" to "Подборка №7",
        "10.m3u" to "Подборка №10",
        "11.m3u" to "Подборка №11",
        "12.m3u" to "Подборка №12",
        "film.m3u" to "Фильмы и сериалы",
    )

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    val allChannels: List<Channel> get() = _state.value.playlists.flatMap { it.channels }

    fun channelById(id: String): Channel? = allChannels.firstOrNull { it.id == id }

    /** Список для переключения каналов (тот же плейлист и категория). */
    fun zappingList(channel: Channel): List<Channel> {
        val playlist = _state.value.playlists.firstOrNull { it.id == channel.playlistId }
            ?: return listOf(channel)
        val sameGroup = playlist.channels.filter { it.group == channel.group }
        return if (sameGroup.size > 1) sameGroup else playlist.channels
    }

    fun groupsOf(playlist: Playlist?): List<String> =
        (playlist?.channels ?: allChannels).map { it.group }.distinct()

    /** Быстро показываем встроенные, затем (опционально) подтягиваем пользовательские по сети. */
    fun initialLoad(loadCustom: Boolean = true) {
        scope.launch {
            val local = readBuiltIn()
            if (!loadCustom) {
                _state.value = RepoState(loading = false, playlists = local)
                return@launch
            }
            _state.value = RepoState(loading = true, playlists = local)
            loadAll(keepBuiltIn = true)
        }
    }

    /** Полное обновление всех источников. */
    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            loadAll(keepBuiltIn = false)
        }
    }

    private fun readBuiltIn(): List<Playlist> {
        val ctx = appContext ?: return emptyList()
        return builtIn.mapNotNull { (file, title) ->
            runCatching {
                val id = file.removeSuffix(".m3u")
                val text = ctx.assets.open("playlists/$file").bufferedReader().use { it.readText() }
                Playlist(id, title, "asset:$file", true, M3uParser.parse(id, text))
            }.getOrNull()
        }
    }

    private suspend fun loadAll(keepBuiltIn: Boolean) {
        var error: String? = null
        val local = if (keepBuiltIn) _state.value.playlists.filter { it.builtIn } else readBuiltIn()
        val custom = mutableListOf<Playlist>()
        for (src in Df.store.customSources()) {
            try {
                val text = withContext(Dispatchers.IO) { httpGet(src.url) }
                custom += Playlist(src.id, src.title, src.url, false, M3uParser.parse(src.id, text))
            } catch (e: Exception) {
                error = "Не загрузился «${src.title}»"
            }
        }
        _state.value = RepoState(loading = false, playlists = local + custom, error = error)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "*/*")
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { return it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
