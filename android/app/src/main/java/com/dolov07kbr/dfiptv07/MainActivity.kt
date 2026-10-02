package com.dolov07kbr.dfiptv07

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dolov07kbr.dfiptv07.data.Channel
import com.dolov07kbr.dfiptv07.data.Df
import com.dolov07kbr.dfiptv07.data.EpgRepository
import com.dolov07kbr.dfiptv07.data.Playlist
import com.dolov07kbr.dfiptv07.data.PlaylistRepository
import com.dolov07kbr.dfiptv07.ui.AboutScreen
import com.dolov07kbr.dfiptv07.ui.BrandLogo
import com.dolov07kbr.dfiptv07.ui.ChannelsScreen
import com.dolov07kbr.dfiptv07.ui.DfBrand
import com.dolov07kbr.dfiptv07.ui.DfTheme
import com.dolov07kbr.dfiptv07.ui.FavoritesScreen
import com.dolov07kbr.dfiptv07.ui.GradientText
import com.dolov07kbr.dfiptv07.ui.HomeScreen
import com.dolov07kbr.dfiptv07.ui.MutedText
import com.dolov07kbr.dfiptv07.ui.ParentalScreen
import com.dolov07kbr.dfiptv07.ui.PlaylistsScreen
import com.dolov07kbr.dfiptv07.ui.PinDialog
import com.dolov07kbr.dfiptv07.ui.SearchScreen
import com.dolov07kbr.dfiptv07.ui.SectionTitle
import com.dolov07kbr.dfiptv07.ui.SettingsScreen
import kotlinx.coroutines.launch

private enum class Section(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Default.Home),
    CHANNELS("Каналы", Icons.Default.LiveTv),
    FAVORITES("Избранное", Icons.Default.Star),
    PLAYLISTS("Плейлисты", Icons.Default.PlaylistPlay),
    SEARCH("Поиск", Icons.Default.Search),
    SETTINGS("Настройки", Icons.Default.Settings),
    PARENTAL("Родительский контроль", Icons.Default.Lock),
    ABOUT("О приложении", Icons.Default.Info),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Df.init(this)
        PlaylistRepository.bind(this)
        PlaylistRepository.initialLoad(loadCustom = Df.store.autoRefresh)
        EpgRepository.refreshIfConfigured()
        setContent { DfTheme { Root() } }
    }
}

private fun Context.isTv(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

private fun startPlayer(context: Context, channel: Channel) {
    Df.store.addRecent(channel)
    context.startActivity(
        Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_CHANNEL_ID, channel.id),
    )
}

@Composable
private fun Root() {
    val repo by PlaylistRepository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var section by remember { mutableStateOf(Section.HOME) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    var favs by remember { mutableStateOf(Df.store.favorites()) }
    var recents by remember { mutableStateOf(Df.store.recents()) }
    var swDecoder by remember { mutableStateOf(Df.store.softwareDecoder) }
    var autoRefresh by remember { mutableStateOf(Df.store.autoRefresh) }
    var epgUrl by remember { mutableStateOf(Df.store.epgUrl) }
    var pinGate by remember { mutableStateOf<Channel?>(null) }
    var backPressedAt by remember { mutableLongStateOf(0L) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recents = Df.store.recents()
                favs = Df.store.favorites()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun toggleFavorite(ch: Channel) {
        Df.store.toggleFavorite(ch.id)
        favs = Df.store.favorites()
    }
    fun play(ch: Channel) {
        if (Df.store.isLocked(ch)) pinGate = ch else startPlayer(context, ch)
    }

    // Обработка системной кнопки «Назад»:
    //  - в под-разделе настроек → вернуться в Настройки;
    //  - не на Главной → на Главную;
    //  - на Главной → выйти (двойное нажатие для защиты от случайности).
    BackHandler {
        when {
            section == Section.PARENTAL || section == Section.ABOUT -> section = Section.SETTINGS
            section != Section.HOME -> {
                section = Section.HOME
                selectedPlaylist = null
                group = null
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - backPressedAt < 2000) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    backPressedAt = now
                    Toast.makeText(context, "Нажмите ещё раз для выхода", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val lockedGroups = remember(favs, repo) { Df.store.lockedGroups() }
    val railItems = listOf(
        Section.HOME, Section.CHANNELS, Section.FAVORITES, Section.PLAYLISTS,
        Section.SEARCH, Section.SETTINGS, Section.PARENTAL, Section.ABOUT,
    )
    // На узких экранах 5 основных вкладок + «Ещё» выпадающим меню
    val barItems = listOf(Section.HOME, Section.CHANNELS, Section.FAVORITES, Section.PLAYLISTS, Section.SEARCH)

    fun goToPlaylist(p: Playlist) {
        selectedPlaylist = p
        group = null
        section = Section.CHANNELS
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(DfBrand.Bg)) {
        val wide = maxWidth >= 840.dp || context.isTv()
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.width(200.dp),
                    containerColor = Color(0xE60E1630),
                    header = {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BrandLogo(modifier = Modifier.size(64.dp))
                            GradientText("DF IPTV", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        }
                    },
                ) {
                    railItems.forEach { item ->
                        NavigationRailItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(item.icon, item.title) },
                            label = { Text(item.title, fontSize = 13.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DfBrand.Cyan,
                                selectedTextColor = DfBrand.Text,
                                indicatorColor = Color(0xFF283477),
                                unselectedIconColor = DfBrand.Muted,
                                unselectedTextColor = DfBrand.Muted,
                            ),
                        )
                    }
                }
                ScreenContent(
                    section = section,
                    onSection = { section = it },
                    repo = repo,
                    selectedPlaylist = selectedPlaylist,
                    onSelectPlaylist = { goToPlaylist(it) },
                    group = group,
                    onGroup = { group = it },
                    favs = favs,
                    lockedGroups = lockedGroups,
                    recents = recents,
                    onPlay = ::play,
                    onToggleFavorite = ::toggleFavorite,
                    swDecoder = swDecoder,
                    onSoftwareDecoder = { swDecoder = it; Df.store.softwareDecoder = it },
                    autoRefresh = autoRefresh,
                    onAutoRefresh = { autoRefresh = it; Df.store.autoRefresh = it },
                    epgUrl = epgUrl,
                    onEpgUrl = { epgUrl = it; Df.store.epgUrl = it; EpgRepository.refreshIfConfigured(force = true) },
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ScreenContent(
                    section = section,
                    onSection = { section = it },
                    repo = repo,
                    selectedPlaylist = selectedPlaylist,
                    onSelectPlaylist = { goToPlaylist(it) },
                    group = group,
                    onGroup = { group = it },
                    favs = favs,
                    lockedGroups = lockedGroups,
                    recents = recents,
                    onPlay = ::play,
                    onToggleFavorite = ::toggleFavorite,
                    swDecoder = swDecoder,
                    onSoftwareDecoder = { swDecoder = it; Df.store.softwareDecoder = it },
                    autoRefresh = autoRefresh,
                    onAutoRefresh = { autoRefresh = it; Df.store.autoRefresh = it },
                    epgUrl = epgUrl,
                    onEpgUrl = { epgUrl = it; Df.store.epgUrl = it; EpgRepository.refreshIfConfigured(force = true) },
                    modifier = Modifier.weight(1f),
                )
                NavigationBar(containerColor = Color(0xE60E1630)) {
                    val phoneItems = barItems + Section.SETTINGS
                    phoneItems.forEach { item ->
                        NavigationBarItem(
                            selected = section == item || (item == Section.SETTINGS && section in listOf(Section.PARENTAL, Section.ABOUT)),
                            onClick = { section = item },
                            icon = { Icon(item.icon, item.title) },
                            label = { Text(item.title, fontSize = 11.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DfBrand.Cyan,
                                selectedTextColor = DfBrand.Text,
                                indicatorColor = Color(0xFF283477),
                                unselectedIconColor = DfBrand.Muted,
                                unselectedTextColor = DfBrand.Muted,
                            ),
                        )
                    }
                }
            }
        }
    }

    pinGate?.let { ch ->
        PinDialog(
            title = "Родительский контроль",
            subtitle = "Категория «${ch.group}» защищена PIN.",
            confirmLabel = "Смотреть",
            onConfirm = { pin ->
                if (Df.store.checkPin(pin)) {
                    pinGate = null
                    startPlayer(context, ch)
                    true
                } else false
            },
            onDismiss = { pinGate = null },
        )
    }
}

@Composable
private fun ScreenContent(
    section: Section,
    onSection: (Section) -> Unit,
    repo: PlaylistRepository.RepoState,
    selectedPlaylist: Playlist?,
    onSelectPlaylist: (Playlist) -> Unit,
    group: String?,
    onGroup: (String?) -> Unit,
    favs: Set<String>,
    lockedGroups: Set<String>,
    recents: List<Channel>,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    swDecoder: Boolean,
    onSoftwareDecoder: (Boolean) -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: (Boolean) -> Unit,
    epgUrl: String,
    onEpgUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        if (section != Section.HOME) {
            SectionTitle(section.title)
            MutedText("DF IPTV • фирменный стиль dolov07kbr.github.io", modifier = Modifier.padding(bottom = 12.dp))
        }
        repo.error?.let { MutedText(it) }
        when (section) {
            Section.HOME -> HomeScreen(
                playlists = repo.playlists,
                loading = repo.loading,
                recents = recents,
                favoritesCount = favs.size,
                favs = favs,
                lockedGroups = lockedGroups,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                onOpenPlaylist = onSelectPlaylist,
            )
            Section.CHANNELS -> ChannelsScreen(
                playlists = repo.playlists,
                selected = selectedPlaylist,
                onSelectPlaylist = onSelectPlaylist,
                group = group,
                onGroup = onGroup,
                favs = favs,
                lockedGroups = lockedGroups,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
            )
            Section.FAVORITES -> FavoritesScreen(
                favs = favs,
                lockedGroups = lockedGroups,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
            )
            Section.PLAYLISTS -> PlaylistsScreen(
                playlists = repo.playlists,
                loading = repo.loading,
                onRefresh = { PlaylistRepository.refresh() },
                onAdd = { title, url ->
                    Df.store.addCustomSource(title, url)
                    PlaylistRepository.refresh()
                },
                onRemove = { id ->
                    Df.store.removeCustomSource(id)
                    PlaylistRepository.refresh()
                },
                onOpen = onSelectPlaylist,
            )
            Section.SEARCH -> SearchScreen(
                favs = favs,
                lockedGroups = lockedGroups,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
            )
            Section.SETTINGS -> SettingsScreen(
                softwareDecoder = swDecoder,
                onSoftwareDecoder = onSoftwareDecoder,
                autoRefresh = autoRefresh,
                onAutoRefresh = onAutoRefresh,
                epgUrl = epgUrl,
                onEpgUrl = onEpgUrl,
                onRefreshPlaylists = { PlaylistRepository.refresh() },
                onOpenParental = { onSection(Section.PARENTAL) },
                onOpenAbout = { onSection(Section.ABOUT) },
            )
            Section.PARENTAL -> ParentalScreen()
            Section.ABOUT -> AboutScreen()
        }
    }
}
