package com.dolov07kbr.dfiptv07.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.dolov07kbr.dfiptv07.BuildConfig
import com.dolov07kbr.dfiptv07.data.Df
import com.dolov07kbr.dfiptv07.data.PlaylistRepository

/** Настройки: плеер, EPG, обновление. */
@Composable
fun SettingsScreen(
    softwareDecoder: Boolean,
    onSoftwareDecoder: (Boolean) -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: (Boolean) -> Unit,
    epgUrl: String,
    onEpgUrl: (String) -> Unit,
    onRefreshPlaylists: () -> Unit,
    onOpenParental: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var epgDraft by remember { mutableStateOf(epgUrl) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { SectionTitle("Плеер") }
        item {
            SwitchRow(
                title = "Программный декодер",
                subtitle = "Принудительно программное декодирование видео (HW/SW)",
                checked = softwareDecoder,
                onChecked = onSoftwareDecoder,
            )
        }
        item { SectionTitle("EPG (телегид)") }
        item {
            Card(shape = DfBrand.ShapeM, colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("URL XMLTV", color = DfBrand.Text, fontWeight = FontWeight.SemiBold)
                    Text("Необязательно: программа передач «сейчас/далее» у каналов.", color = DfBrand.Muted)
                    OutlinedTextField(
                        value = epgDraft,
                        onValueChange = { epgDraft = it },
                        label = { Text("https://…/epg.xml") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    TextButton(onClick = { onEpgUrl(epgDraft.trim()) }) { Text("Сохранить и загрузить") }
                }
            }
        }
        item { SectionTitle("Плейлисты") }
        item {
            SwitchRow(
                title = "Автообновление",
                subtitle = "Подтягивать пользовательские плейлисты при запуске",
                checked = autoRefresh,
                onChecked = onAutoRefresh,
            )
        }
        item { TextButton(onClick = onRefreshPlaylists) { Text("Обновить плейлисты сейчас") } }
        item { SectionTitle("Ещё") }
        item { TextButton(onClick = onOpenParental) { Text("Родительский контроль") } }
        item { TextButton(onClick = onOpenAbout) { Text("О приложении") } }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(shape = DfBrand.ShapeM, colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = DfBrand.Text, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = DfBrand.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(checkedThumbColor = DfBrand.Cyan, checkedTrackColor = DfBrand.Violet.copy(alpha = 0.5f)),
            )
        }
    }
}

/** Родительский контроль: PIN + блокировка категорий. Выключен по умолчанию. */
@Composable
fun ParentalScreen() {
    var version by remember { mutableStateOf(0) }
    val enabled = remember(version) { Df.store.parentalEnabled }
    val locked = remember(version) { Df.store.lockedGroups() }
    val groups = remember { PlaylistRepository.groupsOf(null) }

    var createOpen by remember { mutableStateOf(false) }
    var confirmThen by remember { mutableStateOf<(() -> Unit)?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(shape = DfBrand.ShapeM, colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Родительский контроль", color = DfBrand.Text, fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "Включён: выбранные категории открываются только по PIN."
                    else "Выключен. Ничего не блокируется, пока вы сами не включите.",
                    color = DfBrand.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!enabled) {
                        TextButton(onClick = { createOpen = true }) { Text("Включить и задать PIN") }
                    } else {
                        TextButton(onClick = { confirmThen = { Df.store.pinHash = null; version++ } }) { Text("Выключить") }
                        TextButton(onClick = { confirmThen = { createOpen = true } }) { Text("Сменить PIN") }
                    }
                }
            }
        }
        if (enabled) {
            SectionTitle("Заблокированные категории", modifier = Modifier.padding(top = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it }) { g ->
                    Card(
                        shape = DfBrand.ShapeS,
                        colors = CardDefaults.cardColors(
                            containerColor = if (g in locked) DfBrand.Violet.copy(alpha = 0.22f) else DfBrand.CardDim,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(g, color = DfBrand.Text, modifier = Modifier.weight(1f))
                            IconButton(onClick = { Df.store.toggleLockedGroup(g); version++ }) {
                                Icon(
                                    if (g in locked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    null,
                                    tint = if (g in locked) DfBrand.Cyan else DfBrand.Muted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        PinDialog(
            title = "Новый PIN",
            subtitle = "4 цифры — понадобятся для защищённых категорий.",
            confirmLabel = "Сохранить",
            onConfirm = { pin ->
                if (pin.length != 4) false
                else {
                    Df.store.pinHash = Df.store.hash(pin)
                    createOpen = false
                    version++
                    true
                }
            },
            onDismiss = { createOpen = false },
        )
    }
    confirmThen?.let { action ->
        PinDialog(
            title = "Подтверждение",
            subtitle = "Введите текущий PIN.",
            confirmLabel = "OK",
            onConfirm = { pin ->
                if (Df.store.checkPin(pin)) {
                    confirmThen = null
                    action()
                    true
                } else false
            },
            onDismiss = { confirmThen = null },
        )
    }
}

/** О приложении. */
@Composable
fun AboutScreen() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(shape = DfBrand.ShapeL, colors = CardDefaults.cardColors(containerColor = DfBrand.CardDim)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo(modifier = Modifier.size(96.dp))
                GradientText("DF IPTV", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                Text("версия ${BuildConfig.VERSION_NAME}", color = DfBrand.Muted)
                Text(
                    "Оригинальный IPTV-плеер Dolov07KBR в фирменном стиле сайта.\n" +
                        "Kotlin • Jetpack Compose • Media3\n" +
                        "Лицензия: GPL-3.0\n" +
                        "Плейлисты: репозиторий DF_IPTV",
                    color = DfBrand.Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
