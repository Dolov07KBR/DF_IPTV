package com.dolov07kbr.dfiptv07

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dolov07kbr.dfiptv07.data.Channel
import com.dolov07kbr.dfiptv07.data.Df
import com.dolov07kbr.dfiptv07.data.EpgRepository
import com.dolov07kbr.dfiptv07.data.PlaylistRepository
import com.dolov07kbr.dfiptv07.player.DfRenderersFactory
import com.dolov07kbr.dfiptv07.ui.DfBrand
import com.dolov07kbr.dfiptv07.ui.DfTheme

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
    }

    private lateinit var startChannel: Channel
    private lateinit var zapping: List<Channel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Df.init(this)
        val ch = intent.getStringExtra(EXTRA_CHANNEL_ID)?.let { PlaylistRepository.channelById(it) }
        if (ch == null) {
            finish()
            return
        }
        startChannel = ch
        zapping = PlaylistRepository.zappingList(ch)
        setContent { DfTheme { PlayerScreen() } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPip()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    @Composable
    private fun PlayerScreen() {
        var idx by remember { mutableStateOf(maxOf(0, zapping.indexOf(startChannel))) }
        val ch = zapping.getOrNull(idx) ?: startChannel
        var sw by remember { mutableStateOf(Df.store.softwareDecoder) }
        var resizeMode by remember { mutableStateOf(0) }
        var speed by remember { mutableStateOf(1f) }
        var error by remember { mutableStateOf<String?>(null) }
        var tracksOpen by remember { mutableStateOf(false) }
        var speedOpen by remember { mutableStateOf(false) }

        val player = remember(ch.id, sw) {
            ExoPlayer.Builder(this@PlayerActivity, DfRenderersFactory(this@PlayerActivity, sw)).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        error = e.localizedMessage ?: "Ошибка воспроизведения"
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) error = null
                    }
                })
            }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        LaunchedEffect(player, ch.id) {
            error = null
            player.setMediaItem(MediaItem.fromUri(ch.url))
            player.prepare()
            val saved = Df.store.savedPosition(ch.id)
            if (saved > 0) player.seekTo(saved)
            player.playWhenReady = true
        }
        LaunchedEffect(player, speed) {
            player.setPlaybackParameters(PlaybackParameters(speed))
        }

        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, player, ch.id) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    val dur = player.duration
                    if (dur > 0) Df.store.savePosition(ch.id, player.currentPosition, dur)
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        val resizeModes = listOf(
            "Вписать" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Заполнить" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
            "Обрезать" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        )

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        controllerShowTimeoutMs = 4000
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeModes[resizeMode].second
                },
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC0B1020), Color(0x000B1020))))
                    .padding(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.name,
                            color = DfBrand.Text,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val nn = remember(ch.id) { EpgRepository.nowNext(ch) }
                        Text(
                            nn?.first?.let { "Сейчас: ${it.title}" } ?: ch.group,
                            color = DfBrand.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { idx = (idx - 1 + zapping.size) % zapping.size }) {
                        Icon(Icons.Default.SkipPrevious, "Предыдущий", tint = Color.White)
                    }
                    IconButton(onClick = { idx = (idx + 1) % zapping.size }) {
                        Icon(Icons.Default.SkipNext, "Следующий", tint = Color.White)
                    }
                    IconButton(onClick = { resizeMode = (resizeMode + 1) % resizeModes.size }) {
                        Icon(Icons.Default.AspectRatio, "Экран: ${resizeModes[resizeMode].first}", tint = Color.White)
                    }
                    IconButton(onClick = { speedOpen = true }) {
                        Icon(Icons.Default.Speed, "Скорость", tint = Color.White)
                    }
                    IconButton(onClick = { tracksOpen = true }) {
                        Icon(Icons.Default.Audiotrack, "Дорожки и субтитры", tint = Color.White)
                    }
                    IconButton(onClick = { sw = !sw; Df.store.softwareDecoder = sw }) {
                        Icon(Icons.Default.Memory, if (sw) "Декодер: SW" else "Декодер: HW", tint = if (sw) DfBrand.Cyan else Color.White)
                    }
                    IconButton(onClick = { enterPip() }) {
                        Icon(Icons.Default.PictureInPictureAlt, "Картинка в картинке", tint = Color.White)
                    }
                }
            }

            error?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color(0xCC0B1020)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Не удалось открыть канал", color = DfBrand.Text, fontWeight = FontWeight.Bold)
                        Text(msg, color = DfBrand.Muted)
                        Button(onClick = { error = null; player.prepare() }) { Text("Повторить") }
                    }
                }
            }
        }

        if (tracksOpen) TracksDialog(player) { tracksOpen = false }
        if (speedOpen) SpeedDialog(speed) { s -> speed = s; speedOpen = false }
    }

    @Composable
    private fun TracksDialog(player: ExoPlayer, onDismiss: () -> Unit) {
        val groups = remember(player) {
            player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT }
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Дорожки и субтитры") },
            text = {
                if (groups.isEmpty()) {
                    Text("У этого потока нет выбираемых дорожек.", color = DfBrand.Muted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(groups) { g ->
                            for (i in 0 until g.length) {
                                val fmt = g.getTrackFormat(i)
                                val label = fmt.label ?: fmt.language ?: fmt.sampleMimeType ?: "Дорожка ${i + 1}"
                                val kind = if (g.type == C.TRACK_TYPE_AUDIO) "Аудио" else "Субтитры"
                                Text(
                                    "$kind: $label",
                                    color = DfBrand.Text,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, listOf(i)))
                                                .build()
                                            onDismiss()
                                        }
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    onDismiss()
                }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        )
    }

    @Composable
    private fun SpeedDialog(current: Float, onPick: (Float) -> Unit) {
        val presets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        AlertDialog(
            onDismissRequest = { onPick(current) },
            title = { Text("Скорость") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { p ->
                        Text(
                            "×${p}",
                            color = if (p == current) DfBrand.Cyan else DfBrand.Text,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(6.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Закрыть") } },
        )
    }
}
