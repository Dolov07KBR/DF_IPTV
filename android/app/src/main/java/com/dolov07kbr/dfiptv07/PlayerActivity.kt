package com.dolov07kbr.dfiptv07

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.dolov07kbr.dfiptv07.ui.ChannelLogo
import com.dolov07kbr.dfiptv07.ui.DfBrand
import com.dolov07kbr.dfiptv07.ui.DfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
    }

    private lateinit var startChannel: Channel
    private lateinit var zapping: List<Channel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Не давать экрану гаснуть во время просмотра
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Полноэкранный immersive-режим
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

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

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Обработка кнопок на пульте/гарнитуре
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                zapNext()
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                zapPrev()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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

    // Для вызова из Compose
    private var zapNextHandler: (() -> Unit)? = null
    private var zapPrevHandler: (() -> Unit)? = null
    private fun zapNext() { zapNextHandler?.invoke() }
    private fun zapPrev() { zapPrevHandler?.invoke() }

    @Composable
    private fun PlayerScreen() {
        var idx by remember { mutableStateOf(maxOf(0, zapping.indexOf(startChannel))) }
        val ch = zapping.getOrNull(idx) ?: startChannel
        var sw by remember { mutableStateOf(Df.store.softwareDecoder) }
        var resizeMode by remember { mutableIntStateOf(0) }
        var speed by remember { mutableFloatStateOf(1f) }
        var error by remember { mutableStateOf<String?>(null) }
        var tracksOpen by remember { mutableStateOf(false) }
        var speedOpen by remember { mutableStateOf(false) }
        var listOpen by remember { mutableStateOf(false) }
        var zapToast by remember { mutableStateOf<Channel?>(null) }
        val scope = rememberCoroutineScope()

        // Передаём обработчики zapping для onKeyDown
        DisposableEffect(idx, zapping.size) {
            zapNextHandler = {
                idx = (idx + 1) % zapping.size
                scope.launch {
                    zapToast = zapping.getOrNull(idx)
                    delay(2200)
                    if (zapToast?.id == zapping.getOrNull(idx)?.id) zapToast = null
                }
            }
            zapPrevHandler = {
                idx = (idx - 1 + zapping.size) % zapping.size
                scope.launch {
                    zapToast = zapping.getOrNull(idx)
                    delay(2200)
                    if (zapToast?.id == zapping.getOrNull(idx)?.id) zapToast = null
                }
            }
            onDispose { zapNextHandler = null; zapPrevHandler = null }
        }

        val player = remember(ch.id, sw) {
            ExoPlayer.Builder(this@PlayerActivity, DfRenderersFactory(this@PlayerActivity, sw)).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        error = e.localizedMessage ?: "Ошибка воспроизведения"
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) error = null
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            // Когда поток пошёл — покажем оверлей с названием канала на 2 с
                            scope.launch {
                                zapToast = ch
                                delay(2200)
                                if (zapToast?.id == ch.id) zapToast = null
                            }
                        }
                    }
                })
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }

        LaunchedEffect(player, ch.id) {
            error = null
            player.setMediaItem(MediaItem.fromUri(ch.url))
            player.prepare()
            val saved = Df.store.savedPosition(ch.id)
            if (saved > 0) player.seekTo(saved)
            player.playWhenReady = true
        }
        LaunchedEffect(player, speed) { player.setPlaybackParameters(PlaybackParameters(speed)) }

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

        // Системный back — закрываем панель каналов, если она открыта
        BackHandler(enabled = listOpen) { listOpen = false }

        val resizeModes = listOf(
            "Вписать" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Заполнить" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
            "Обрезать" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        )

        // Перетаскивание по горизонтали → переключение каналов (swipe)
        var dragAccum by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragAccum > 80f -> zapPrev()
                                dragAccum < -80f -> zapNext()
                            }
                            dragAccum = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                    )
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        controllerShowTimeoutMs = 4000
                        controllerAutoShow = true
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeModes[resizeMode].second
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Верхняя панель
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xDD0B1020), Color(0x000B1020))))
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 20.dp),
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
                            fontSize = 16.sp,
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
                    // Пред/след — большие кнопки
                    IconButton(onClick = { zapPrev() }) {
                        Icon(Icons.Default.SkipPrevious, "Предыдущий", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { zapNext() }) {
                        Icon(Icons.Default.SkipNext, "Следующий", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Нижняя панель инструментов (дополнительно к стандартному контроллеру)
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0x000B1020), Color(0xDD0B1020))))
                    .padding(top = 30.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { resizeMode = (resizeMode + 1) % resizeModes.size }) {
                    Icon(Icons.Default.AspectRatio, resizeModes[resizeMode].first, tint = Color.White)
                }
                IconButton(onClick = { speedOpen = true }) {
                    Icon(Icons.Default.Speed, "Скорость", tint = Color.White)
                }
                IconButton(onClick = { tracksOpen = true }) {
                    Icon(Icons.Default.Audiotrack, "Дорожки", tint = Color.White)
                }
                IconButton(onClick = { sw = !sw; Df.store.softwareDecoder = sw }) {
                    Icon(Icons.Default.Memory, if (sw) "SW-декодер" else "HW-декодер", tint = if (sw) DfBrand.Cyan else Color.White)
                }
                IconButton(onClick = { listOpen = true }) {
                    Icon(Icons.Default.List, "Список каналов", tint = Color.White)
                }
                IconButton(onClick = { enterPip() }) {
                    Icon(Icons.Default.PictureInPictureAlt, "Картинка в картинке", tint = Color.White)
                }
            }

            // Большой оверлей при переключении канала
            AnimatedVisibility(
                visible = zapToast != null && zapToast?.id == ch.id && player.playbackState != Player.STATE_IDLE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    color = Color(0xCC0B1020),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelLogo(ch, size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                ch.name,
                                color = DfBrand.Text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                            )
                            Text(
                                ch.group,
                                color = DfBrand.Cyan,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            val en = EpgRepository.nowNext(ch)?.first
                            if (en != null) Text(
                                "Сейчас: ${en.title}",
                                color = DfBrand.Muted,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Буферизация
            if (player.playbackState == Player.STATE_BUFFERING && error == null) {
                CircularProgressIndicator(
                    color = DfBrand.Cyan,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Ошибка
            error?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color(0xCC0B1020)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Не удалось открыть канал", color = DfBrand.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(msg, color = DfBrand.Muted, modifier = Modifier.padding(horizontal = 24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { error = null; player.prepare() }) { Text("Повторить") }
                            TextButton(onClick = { zapNext() }) { Text("След. канал", color = DfBrand.Cyan) }
                        }
                    }
                }
            }
        }

        // Панель списка каналов (выезжает справа)
        if (listOpen) {
            Box(Modifier.fillMaxSize().background(Color(0x88000000))) {
                // Клик снаружи — закрыть
                Box(Modifier.fillMaxSize().clickable { listOpen = false })
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, idx - 2))
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .align(Alignment.CenterEnd)
                        .fillMaxSize()
                        .background(DfBrand.Bg2, RoundedCornerShape(22.dp, 0.dp, 0.dp, 22.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        IconButton(onClick = { listOpen = false }) {
                            Icon(Icons.Default.Close, "Закрыть", tint = DfBrand.Text)
                        }
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text("Каналы", color = DfBrand.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "${zapping.size} каналов • ${ch.group}",
                                color = DfBrand.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(zapping, key = { it.id }) { item ->
                            val active = item.id == ch.id
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (active) DfBrand.Violet.copy(alpha = 0.25f) else DfBrand.CardDim,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        idx = zapping.indexOf(item).takeIf { it >= 0 } ?: idx
                                        listOpen = false
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChannelLogo(item, size = 40.dp)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp),
                                    ) {
                                        Text(
                                            item.name,
                                            color = if (active) DfBrand.Cyan else DfBrand.Text,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp,
                                        )
                                        Text(
                                            EpgRepository.nowNext(item)?.first?.title ?: item.group,
                                            color = DfBrand.Muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (active) {
                                        Icon(Icons.Default.ChevronRight, null, tint = DfBrand.Cyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (tracksOpen) TracksDialog(player) { tracksOpen = false }
        if (speedOpen) SpeedDialog(speed) { s -> speed = s; speedOpen = false }
    }

    @Composable
    private fun TracksDialog(player: ExoPlayer, onDismiss: () -> Unit) {
        val groups = remember(player.currentMediaItemIndex, player.playbackState) {
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
                            "×$p",
                            color = if (p == current) DfBrand.Cyan else DfBrand.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(6.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Закрыть") } },
        )
    }
}
