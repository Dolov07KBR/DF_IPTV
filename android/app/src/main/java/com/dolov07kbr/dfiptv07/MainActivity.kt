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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private enum class Section(val title:String){ HOME("Главная"), CHANNELS("Каналы"), FAVORITES("Избранное"), PLAYLISTS("Плейлисты"), SEARCH("Поиск"), PARENTAL("Родительский контроль"), SETTINGS("Настройки"), ABOUT("О приложении") }

@Composable private fun DfTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary=Color(0xFF8B5CF6),secondary=Color(0xFF22D3EE),background=Color(0xFF070D1F),surface=Color(0xFF111A35)),
    content=content
)

@Composable private fun DfIptvApp(playlists: List<Playlist>, onPlay: (Channel)->Unit) {
    var section by remember { mutableStateOf(Section.HOME) }
    var selected by remember { mutableStateOf(playlists.firstOrNull()) }
    var query by remember { mutableStateOf("") }
    var pinOpen by remember { mutableStateOf(false) }
    val favorites = remember { mutableStateListOf<String>() }
    val allChannels = playlists.flatMap { it.channels }.distinctBy { it.url }
    val visible = when(section){
        Section.FAVORITES -> allChannels.filter { it.url in favorites }
        Section.SEARCH -> allChannels.filter { it.name.contains(query,true)||it.group.contains(query,true) }
        else -> selected?.channels.orEmpty()
    }
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A2763),Color(0xFF070D1F))))) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(modifier=Modifier.width(190.dp),containerColor=Color(0xE60A1128),header={
                Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("DF",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xFF22D3EE));Text("IPTV_07",color=Color.White,fontWeight=FontWeight.Bold)}
            }) {
                MenuItem(section,Section.HOME,Icons.Default.Home){section=it}
                MenuItem(section,Section.CHANNELS,Icons.Default.LiveTv){section=it}
                MenuItem(section,Section.FAVORITES,Icons.Default.Star){section=it}
                MenuItem(section,Section.PLAYLISTS,Icons.Default.PlaylistPlay){section=it}
                MenuItem(section,Section.SEARCH,Icons.Default.Search){section=it}
                Spacer(Modifier.weight(1f))
                MenuItem(section,Section.PARENTAL,Icons.Default.Lock){section=it;pinOpen=true}
                MenuItem(section,Section.SETTINGS,Icons.Default.Settings){section=it}
                MenuItem(section,Section.ABOUT,Icons.Default.Info){section=it}
            }
            Column(Modifier.weight(1f).padding(24.dp)) {
                Text(section.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White)
                Text("DF IPTV_07 • встроенные плейлисты",color=Color(0xFF9AA6CA),modifier=Modifier.padding(bottom=14.dp))
                when(section){
                    Section.HOME -> HomeContent(playlists,selected,{selected=it;section=Section.CHANNELS})
                    Section.PLAYLISTS -> PlaylistContent(playlists,selected){selected=it;section=Section.CHANNELS}
                    Section.SEARCH -> { OutlinedTextField(query,{query=it},leadingIcon={Icon(Icons.Default.Search,null)},label={Text("Название канала или категория")},modifier=Modifier.fillMaxWidth().padding(bottom=14.dp),singleLine=true); ChannelList(visible,favorites,onPlay) }
                    Section.SETTINGS -> SettingsContent()
                    Section.ABOUT -> AboutContent()
                    Section.PARENTAL -> Text("Контроль выключен. Ничего не блокируется без выбора пользователя.",color=Color.White)
                    else -> ChannelList(visible,favorites,onPlay)
                }
            }
        }
    }
    if(pinOpen) ParentalDialog{pinOpen=false;section=Section.SETTINGS}
}

@Composable private fun MenuItem(current:Section,item:Section,icon:androidx.compose.ui.graphics.vector.ImageVector,onSelect:(Section)->Unit){
    NavigationRailItem(selected=current==item,onClick={onSelect(item)},icon={Icon(icon,item.title)},label={Text(item.title)},alwaysShowLabel=true,colors=NavigationRailItemDefaults.colors(selectedIconColor=Color(0xFF22D3EE),selectedTextColor=Color.White,indicatorColor=Color(0xFF283477)))
}

@Composable private fun HomeContent(playlists:List<Playlist>,selected:Playlist?,open:(Playlist)->Unit){
    Card(colors=CardDefaults.cardColors(containerColor=Color(0xDD111A35)),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().padding(bottom=18.dp)){Column(Modifier.padding(22.dp)){Text("Добро пожаловать в DF IPTV_07",fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Выберите подборку и начните просмотр. Все источники уже внутри приложения.",color=Color(0xFF9AA6CA));Text("${playlists.sumOf{it.channels.size}} каналов • ${playlists.size} плейлистов",color=Color(0xFF22D3EE),modifier=Modifier.padding(top=10.dp))}}
    PlaylistContent(playlists,selected,open)
}

@Composable private fun PlaylistContent(playlists:List<Playlist>,selected:Playlist?,open:(Playlist)->Unit){
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(playlists){p->Card(Modifier.fillMaxWidth().clickable{open(p)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=if(selected==p)Color(0xFF283477)else Color(0xDD111A35))){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(if(p.file.contains("film",true))"🎬" else "📺",fontSize=28.sp);Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(p.title,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text("${p.channels.size} каналов",color=Color(0xFF9AA6CA))};Icon(Icons.Default.ChevronRight,null,tint=Color(0xFF22D3EE))}}}}
}

@Composable private fun ChannelList(channels:List<Channel>,favorites:MutableList<String>,onPlay:(Channel)->Unit){
    if(channels.isEmpty()){Text("Здесь пока ничего нет",color=Color(0xFF9AA6CA));return}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(channels,key={it.url+it.name}){c->Card(Modifier.fillMaxWidth().clickable{onPlay(c)},shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color(0xDD111A35))){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(if(c.group.contains("дет",true))"🧸" else if(c.group.contains("фильм",true))"🎬" else "📡",fontSize=24.sp);Column(Modifier.weight(1f).padding(horizontal=13.dp)){Text(c.name,color=Color.White,fontSize=17.sp);Text(c.group,color=Color(0xFF9AA6CA))};IconButton({if(c.url in favorites)favorites.remove(c.url)else favorites.add(c.url)}){Icon(if(c.url in favorites)Icons.Default.Star else Icons.Default.StarBorder,"Избранное",tint=Color(0xFFFFD54F))};Icon(Icons.Default.PlayArrow,"Смотреть",tint=Color(0xFF22D3EE))}}}}
}

@Composable private fun SettingsContent(){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){SettingCard("🎨","Оформление","Фирменная тёмная тема DF");SettingCard("📺","Режим Android TV","Крупный интерфейс и управление пультом");SettingCard("🔄","Обновление плейлистов","Встроенные списки обновляются с новой версией APK")}}
@Composable private fun SettingCard(icon:String,title:String,text:String){Card(colors=CardDefaults.cardColors(containerColor=Color(0xDD111A35)),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp)){Text(icon,fontSize=25.sp);Column(Modifier.padding(start=14.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(text,color=Color(0xFF9AA6CA))}}}}
@Composable private fun AboutContent(){Column{Text("DF IPTV_07",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("Оригинальный IPTV-плеер Dolov07KBR",color=Color(0xFF22D3EE));Text("Kotlin • Jetpack Compose • Media3\nЛицензия: GPL-3.0\nПлейлисты: репозиторий DF_IPTV",color=Color(0xFF9AA6CA),modifier=Modifier.padding(top=16.dp))}}

@Composable private fun ParentalDialog(onDismiss:()->Unit){var pin by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("Родительский контроль")},text={Column{Text("По умолчанию ничего не блокируется. PIN потребуется только для вручную защищённых каналов.");OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(4)},label={Text("Новый PIN")})}},confirmButton={Button(onClick=onDismiss,enabled=pin.length==4){Text("Сохранить")}},dismissButton={TextButton(onClick=onDismiss){Text("Позже")}})}
