package com.dolov07kbr.dfiptv07.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dolov07kbr.dfiptv07.data.Channel
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Кэш логотипов каналов + загрузка по сети. */
object LogoCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            try {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }
}

/** Логотип канала с фолбэком в градиентную плашку с буквой. */
@Composable
fun ChannelLogo(channel: Channel, size: Dp = 46.dp) {
    var bmp by remember(channel.id) { mutableStateOf(channel.logo?.let { LogoCache.get(it) }) }
    LaunchedEffect(channel.id) {
        val url = channel.logo ?: return@LaunchedEffect
        if (bmp == null) bmp = LogoCache.load(url)
    }
    val bitmap = bmp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(DfBrand.Grad),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = channel.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
