package com.dolov07kbr.dfiptv07.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Глобальная инициализация хранилища. */
object Df {
    lateinit var store: Store
        private set

    fun init(context: Context) {
        if (!::store.isInitialized) store = Store(context.applicationContext)
    }
}

/**
 * Все настройки приложения в SharedPreferences:
 * избранное, недавние, свои плейлисты, PIN, блокировки, плеер, позиции.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("df_iptv_07", Context.MODE_PRIVATE)

    // ---------- избранное ----------
    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAV, emptySet())!!.toSet()

    fun isFavorite(channelId: String): Boolean = channelId in favorites()

    /** @return true если теперь в избранном */
    fun toggleFavorite(channelId: String): Boolean {
        val set = favorites().toMutableSet()
        val added = if (!set.remove(channelId)) {
            set.add(channelId); true
        } else false
        prefs.edit().putStringSet(KEY_FAV, set).apply()
        return added
    }

    // ---------- недавние ----------
    fun recents(): List<Channel> = try {
        val arr = JSONArray(prefs.getString(KEY_RECENTS, "[]") ?: "[]")
        List(arr.length()) { i -> channelFromJson(arr.getJSONObject(i)) }
    } catch (_: Exception) {
        emptyList()
    }

    fun addRecent(channel: Channel) {
        val arr = JSONArray()
        arr.put(channelToJson(channel))
        val old = recents()
        for (ch in old) {
            if (ch.id != channel.id && arr.length() < 60) arr.put(channelToJson(ch))
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    // ---------- свои плейлисты ----------
    fun customSources(): List<CustomSource> = try {
        val arr = JSONArray(prefs.getString(KEY_SOURCES, "[]") ?: "[]")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CustomSource(o.getString("id"), o.getString("title"), o.getString("url"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun addCustomSource(title: String, url: String): CustomSource {
        val src = CustomSource("u${System.currentTimeMillis()}", title.ifBlank { "Мой плейлист" }, url)
        val arr = JSONArray()
        customSources().forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)) }
        arr.put(JSONObject().put("id", src.id).put("title", src.title).put("url", src.url))
        prefs.edit().putString(KEY_SOURCES, arr.toString()).apply()
        return src
    }

    fun removeCustomSource(id: String) {
        val arr = JSONArray()
        customSources().filter { it.id != id }.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url))
        }
        prefs.edit().putString(KEY_SOURCES, arr.toString()).apply()
    }

    // ---------- настройки плеера ----------
    var softwareDecoder: Boolean
        get() = prefs.getBoolean(KEY_SW_DECODER, false)
        set(v) { prefs.edit().putBoolean(KEY_SW_DECODER, v).apply() }

    var epgUrl: String
        get() = prefs.getString(KEY_EPG_URL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_EPG_URL, v).apply() }

    var autoRefresh: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REFRESH, true)
        set(v) { prefs.edit().putBoolean(KEY_AUTO_REFRESH, v).apply() }

    // ---------- родительский контроль ----------
    var pinHash: String?
        get() = prefs.getString(KEY_PIN, null)
        set(v) { prefs.edit().putString(KEY_PIN, v).apply() }

    val parentalEnabled: Boolean get() = !pinHash.isNullOrEmpty()

    fun checkPin(pin: String): Boolean = pinHash == hash(pin)

    fun lockedGroups(): Set<String> = prefs.getStringSet(KEY_LOCKED, emptySet())!!.toSet()

    fun setLockedGroups(groups: Set<String>) { prefs.edit().putStringSet(KEY_LOCKED, groups).apply() }

    fun toggleLockedGroup(group: String): Boolean {
        val set = lockedGroups().toMutableSet()
        val added = if (!set.remove(group)) {
            set.add(group); true
        } else false
        setLockedGroups(set)
        return added
    }

    fun isLocked(channel: Channel): Boolean = parentalEnabled && channel.group in lockedGroups()

    // ---------- позиции воспроизведения ----------
    fun savePosition(channelId: String, positionMs: Long, durationMs: Long) {
        prefs.edit().putLong("pos_$channelId", positionMs).putLong("dur_$channelId", durationMs).apply()
    }

    fun savedPosition(channelId: String): Long {
        val pos = prefs.getLong("pos_$channelId", 0L)
        val dur = prefs.getLong("dur_$channelId", 0L)
        if (pos < 8_000) return 0L
        if (dur > 0 && pos > dur - 20_000) return 0L
        return pos
    }

    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ---------- сериализация канала ----------
    private fun channelToJson(ch: Channel): JSONObject = JSONObject()
        .put("id", ch.id).put("name", ch.name).put("url", ch.url)
        .put("group", ch.group).put("logo", ch.logo ?: "")
        .put("tvgId", ch.tvgId ?: "").put("playlistId", ch.playlistId)

    private fun channelFromJson(o: JSONObject): Channel = Channel(
        id = o.optString("id"),
        name = o.optString("name", "Канал"),
        url = o.optString("url"),
        group = o.optString("group", "Без категории"),
        logo = o.optString("logo", "").ifBlank { null },
        tvgId = o.optString("tvgId", "").ifBlank { null },
        playlistId = o.optString("playlistId", ""),
    )

    companion object {
        private const val KEY_FAV = "favorites"
        private const val KEY_RECENTS = "recents"
        private const val KEY_SOURCES = "custom_sources"
        private const val KEY_SW_DECODER = "software_decoder"
        private const val KEY_EPG_URL = "epg_url"
        private const val KEY_AUTO_REFRESH = "auto_refresh"
        private const val KEY_PIN = "pin_hash"
        private const val KEY_LOCKED = "locked_groups"
    }
}
