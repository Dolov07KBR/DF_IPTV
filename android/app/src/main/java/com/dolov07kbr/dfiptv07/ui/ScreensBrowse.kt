package com.dolov07kbr.dfiptv07.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dolov07kbr.dfiptv07.data.Channel
import com.dolov07kbr.dfiptv07.data.Playlist
import com.dolov07kbr.dfiptv07.data.PlaylistRepository

/** Главный экран: герой с логотипом, статистика, недавние, плейлисты. */
@Composable
fun HomeScreen(
    playlists: List<Playlist>,
    loading: Boolean,
    recents: List<Channel>,
    favoritesCount: Int,
    favs: Set<String>,
    lockedGroups: Set<String>,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(
                shape = DfBrand.ShapeL,
                colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandLogo(modifier = Modifier.size(84.dp))
                    Column(modifier = Modifier.padding(start = 18.dp)) {
                        GradientText("DF IPTV", style = MaterialTheme.typography.headlineMedium)
                        MutedText("ТВ, фильмы и музыка — все плейлисты уже внутри")
                        Text(
                            text = "${playlists.sumOf { it.channels.size }} каналов • ${playlists.size} плейлистов • $favoritesCount в избранном",
                            color = DfBrand.Cyan,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        if (loading) {
            item { CircularProgressIndicator(color = DfBrand.Cyan) }
        }
        if (recents.isNotEmpty()) {
            item { SectionTitle("Недавно смотрели") }
            items(recents.take(6), key = { "r${it.id}" }) { ch ->
                ChannelRow(
                    channel = ch,
                    isFavorite = ch.id in favs,
                    isLocked = ch.group in lockedGroups,
                    onPlay = { onPlay(ch) },
                    onToggleFavorite = { onToggleFavorite(ch) },
                )
            }
        }
        item { SectionTitle("Плейлисты") }
        items(playlists, key = { it.id }) { p ->
            PlaylistCard(p, onClick = { onOpenPlaylist(p) })
        }
    }
}

@Composable
fun PlaylistCard(p: Playlist, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = DfBrand.ShapeM,
        colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (p.title.contains("фильм", true) || p.title.contains("Фильм")) Icons.Default.Movie else Icons.Default.PlaylistPlay,
                null,
                tint = DfBrand.Violet,
                modifier = Modifier.size(30.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(p.title, color = DfBrand.Text, fontWeight = FontWeight.SemiBold)
                Text("${p.channels.size} каналов", color = DfBrand.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (trailing != null) trailing()
        }
    }
}

/** Экран каналов: плейлисты, категории, список. */
@Composable
fun ChannelsScreen(
    playlists: List<Playlist>,
    selected: Playlist?,
    onSelectPlaylist: (Playlist) -> Unit,
    group: String?,
    onGroup: (String?) -> Unit,
    favs: Set<String>,
    lockedGroups: Set<String>,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    val channels = remember(selected, group) {
        val base = selected?.channels ?: PlaylistRepository.allChannels
        if (group == null) base else base.filter { it.group == group }
    }
    val groups = remember(selected) { PlaylistRepository.groupsOf(selected) }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            items(playlists, key = { it.id }) { p ->
                Chip(p.title, selected == p) { onSelectPlaylist(p) }
            }
        }
        GroupRail(groups, group, onGroup)
        Text(
            text = "${channels.size} каналов",
            color = DfBrand.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (channels.isEmpty()) {
            EmptyState("Здесь пока ничего нет")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels, key = { it.id }) { ch ->
                    ChannelRow(
                        channel = ch,
                        isFavorite = ch.id in favs,
                        isLocked = ch.group in lockedGroups,
                        onPlay = { onPlay(ch) },
                        onToggleFavorite = { onToggleFavorite(ch) },
                    )
                }
            }
        }
    }
}

/** Поиск по всем каналам. */
@Composable
fun SearchScreen(
    favs: Set<String>,
    lockedGroups: Set<String>,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.isBlank()) emptyList()
        else PlaylistRepository.allChannels
            .filter { it.name.contains(query, true) || it.group.contains(query, true) }
            .take(300)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text("Канал или категория") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isBlank()) {
            EmptyState("Начните вводить — ищем по ${PlaylistRepository.allChannels.size} каналам")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                items(results, key = { it.id }) { ch ->
                    ChannelRow(
                        channel = ch,
                        isFavorite = ch.id in favs,
                        isLocked = ch.group in lockedGroups,
                        onPlay = { onPlay(ch) },
                        onToggleFavorite = { onToggleFavorite(ch) },
                    )
                }
            }
        }
    }
}

/** Избранное. */
@Composable
fun FavoritesScreen(
    favs: Set<String>,
    lockedGroups: Set<String>,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    val list = remember(favs) { PlaylistRepository.allChannels.filter { it.id in favs } }
    if (list.isEmpty()) {
        EmptyState("Отмечайте каналы звездой — они появятся здесь")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { ch ->
                ChannelRow(
                    channel = ch,
                    isFavorite = true,
                    isLocked = ch.group in lockedGroups,
                    onPlay = { onPlay(ch) },
                    onToggleFavorite = { onToggleFavorite(ch) },
                )
            }
        }
    }
}

/** Менеджер плейлистов: встроенные + свои, добавление/удаление/обновление. */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (Playlist) -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { addOpen = true }) {
                Icon(Icons.Default.Add, null); Text("Добавить M3U")
            }
            TextButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Default.Refresh, null); Text("Обновить")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(playlists, key = { it.id }) { p ->
                PlaylistCard(
                    p,
                    onClick = { onOpen(p) },
                    trailing = if (!p.builtIn) {
                        { IconButton(onClick = { onRemove(p.id) }) { Icon(Icons.Default.Delete, "Удалить", tint = DfBrand.Muted) } }
                    } else null,
                )
            }
        }
    }
    if (addOpen) AddPlaylistDialog(onAdd = onAdd, onDismiss = { addOpen = false })
}
