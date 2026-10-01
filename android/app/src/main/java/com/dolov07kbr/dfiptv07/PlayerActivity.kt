package com.dolov07kbr.dfiptv07

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);title=intent.getStringExtra("name")?:"DF IPTV_07";val url=intent.getStringExtra("url")?:return finish();setContent{Player(url)}}
 @Composable private fun Player(url:String){val player=remember{ExoPlayer.Builder(this).build().apply{setMediaItem(MediaItem.fromUri(url));prepare();playWhenReady=true}};DisposableEffect(Unit){onDispose{player.release()}};AndroidView({PlayerView(it).apply{this.player=player;useController=true}},Modifier.fillMaxSize())}
}
