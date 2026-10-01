package com.dolov07kbr.dfiptv07.data

/** Один канал из M3U. */
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
    val playlistId: String,
)

/** Плейлист: встроенный (asset) или пользовательский (url). */
data class Playlist(
    val id: String,
    val title: String,
    val source: String,
    val builtIn: Boolean,
    val channels: List<Channel>,
)

/** Пользовательский источник M3U. */
data class CustomSource(
    val id: String,
    val title: String,
    val url: String,
)

/** Программа из XMLTV EPG. */
data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
)
