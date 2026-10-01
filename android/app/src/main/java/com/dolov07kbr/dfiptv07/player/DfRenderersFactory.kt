package com.dolov07kbr.dfiptv07.player

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Фабрика рендереров с переключателем HW/SW:
 * в программном режиме исключаем аппаратные кодеки (qcom/qti/exynos/mtk/omx).
 */
class DfRenderersFactory(
    context: Context,
    private val softwareOnly: Boolean,
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
    ): Array<MediaCodecVideoRenderer> = super.buildVideoRenderers(
        context,
        extensionRendererMode,
        if (softwareOnly) SOFTWARE_SELECTOR else mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        allowedVideoJoiningTimeMs,
    )

    companion object {
        private val HW_MARKERS = listOf(".hw.", ".qcom.", ".qti.", ".exynos.", ".mtk.", "omx.qcom", "omx.sec", "c2.qti", "c2.qcom", "c2.exynos", "c2.mtk")

        private val SOFTWARE_SELECTOR = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                .filter { info ->
                    val n = info.name.lowercase()
                    HW_MARKERS.none { n.contains(it) }
                }
        }
    }
}
