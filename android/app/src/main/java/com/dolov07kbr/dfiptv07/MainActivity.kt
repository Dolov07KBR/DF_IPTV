package com.dolov07kbr.dfiptv07

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playlists = PlaylistRepository.loadBundled(this)
        setContent { DfTheme { DfIptvApp(playlists) { play(it) } } }
    }
    private fun play(channel: Channel) = startActivity(Intent(this, PlayerActivity::class.java).apply {
        putExtra("name", channel.name); putExtra("url", channel.url)
    })
}

@Composable private fun DfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF8B5CF6),secondary=Color(0xFF22D3EE),background=Color(0xFF070D1F),surface=Color(0xFF111A35)), content=content)
}

@Composable private fun DfIptvApp(playlists: List<Playlist>, onPlay: (Channel)->Unit) {
    var selected by remember { mutableStateOf(playlists.firstOrNull()) }
    var query by remember { mutableStateOf("") }
    var pinOpen by remember { mutableStateOf(false) }
    val channels = selected?.channels.orEmpty().filter { it.name.contains(query,true) || it.group.contains(query,true) }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF18235A),Color(0xFF070D1F))))) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor=Color(0xCC0B122A)) {
                Text("DF",fontSize=25.sp,color=Color(0xFF22D3EE),modifier=Modifier.padding(18.dp))
                playlists.forEach { p -> NavigationRailItem(selected==p,{selected=p},icon={Text("📺")},label={Text(p.title,maxLines=1)}) }
                Spacer(Modifier.weight(1f)); IconButton({pinOpen=true}){Icon(Icons.Default.Lock,"Родительский контроль")}
            }
            Column(Modifier.weight(1f).padding(22.dp)) {
                Text("DF IPTV_07",style=MaterialTheme.typography.headlineMedium,color=Color.White)
                Text(selected?.title ?: "Плейлисты не найдены",color=Color(0xFF9AA6CA))
                OutlinedTextField(query,{query=it},leadingIcon={Icon(Icons.Default.Search,null)},label={Text("Поиск каналов")},modifier=Modifier.fillMaxWidth().padding(vertical=14.dp),singleLine=true)
                LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    items(channels,key={it.url+it.name}) { c ->
                        Card(Modifier.fillMaxWidth().clickable{onPlay(c)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xDD111A35))) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(c.group.contains("дет",true)) "🧸" else if(c.group.contains("фильм",true)) "🎬" else "📡",fontSize=25.sp)
                                Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(c.name,color=Color.White,fontSize=18.sp);Text(c.group,color=Color(0xFF9AA6CA))}
                                Icon(Icons.Default.PlayArrow,"Смотреть",tint=Color(0xFF22D3EE))
                            }
                        }
                    }
                }
            }
        }
    }
    if(pinOpen) ParentalDialog(onDismiss={pinOpen=false})
}

@Composable private fun ParentalDialog(onDismiss:()->Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Родительский контроль")},text={Column{Text("По умолчанию ничего не блокируется. Установите PIN, чтобы позже защищать выбранные каналы и категории.");OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(4)},label={Text("PIN из 4 цифр")})}},confirmButton={Button(onClick=onDismiss,enabled=pin.length==4){Text("Сохранить")}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}
