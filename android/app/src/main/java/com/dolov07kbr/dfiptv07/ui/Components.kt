package com.dolov07kbr.dfiptv07.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dolov07kbr.dfiptv07.data.Channel
import com.dolov07kbr.dfiptv07.data.EpgRepository

/** Строка канала в стиле Televizo: логотип, название, категория, «сейчас/далее», звезда, play. */
@Composable
fun ChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    isLocked: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val epg by EpgRepository.state.collectAsState()
    val nowNext = remember(channel.id, epg) { EpgRepository.nowNext(channel) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = DfBrand.ShapeM,
        colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = channel.name,
                    color = DfBrand.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val now = nowNext?.first
                if (now != null) {
                    Text(
                        text = "Сейчас: ${now.title}",
                        color = DfBrand.Cyan,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = channel.group,
                        color = DfBrand.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isLocked) Icon(Icons.Default.Lock, "Защищено", tint = DfBrand.Muted, modifier = Modifier.size(18.dp))
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    "Избранное",
                    tint = if (isFavorite) DfBrand.Star else DfBrand.Muted,
                )
            }
            Icon(Icons.Default.PlayArrow, "Смотреть", tint = DfBrand.Cyan)
        }
    }
}

/** Горизонтальная лента категорий. */
@Composable
fun GroupRail(groups: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        item { Chip("Все", selected == null) { onSelect(null) } }
        items(groups) { g -> Chip(g, selected == g) { onSelect(g) } }
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (active) DfBrand.Violet.copy(alpha = 0.25f) else DfBrand.CardDim,
        border = BorderStroke(1.dp, if (active) DfBrand.Cyan else DfBrand.Line),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (active) DfBrand.Text else DfBrand.Muted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DfBrand.Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Диалог ввода PIN (4 цифры). onConfirm возвращает true при успехе. */
@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, color = DfBrand.Muted)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4); error = false },
                    label = { Text("PIN (4 цифры)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                if (error) Text("Неверный PIN", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onConfirm(pin)) error = true }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Диалог добавления своего M3U-плейлиста по URL. */
@Composable
fun AddPlaylistDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой плейлист") },
        text = {
            Column {
                Text("Вставьте прямую ссылку на M3U — как в Televizo.", color = DfBrand.Muted)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://…/playlist.m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.startsWith("http"),
                onClick = { onAdd(title.trim(), url.trim()); onDismiss() },
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
