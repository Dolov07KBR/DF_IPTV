package com.dolov07kbr.dfiptv07

import android.content.Context

data class Channel(val name:String,val url:String,val group:String="Без категории",val logo:String?=null)
data class Playlist(val title:String,val file:String,val channels:List<Channel>)

object PlaylistRepository {
    private val files = listOf("DF_IPTV.m3u","07.m3u","10.m3u","11.m3u","12.m3u","film.m3u")
    fun loadBundled(context: Context): List<Playlist> = files.mapNotNull { file ->
        runCatching {
            val lines=context.assets.open("playlists/$file").bufferedReader().readLines()
            Playlist(file.removeSuffix(".m3u").replace('_',' '),file,parse(lines))
        }.getOrNull()
    }
    private fun parse(lines:List<String>):List<Channel>{
        val out=mutableListOf<Channel>(); var name="Канал"; var group="Без категории"; var logo:String?=null
        lines.forEach { raw -> val line=raw.trim()
            if(line.startsWith("#EXTINF",true)){
                name=line.substringAfter(',',"Канал").trim().ifBlank{"Канал"}
                group=attr(line,"group-title") ?: "Без категории"; logo=attr(line,"tvg-logo")
            } else if(line.isNotBlank() && !line.startsWith("#")) out += Channel(name,line,group,logo)
        }; return out.distinctBy{it.url}
    }
    private fun attr(line:String,key:String):String?=Regex("$key=\\\"([^\\\"]*)\\\"",RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
}
