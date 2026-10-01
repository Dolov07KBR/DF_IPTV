package com.dolov07kbr.dfiptv07.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

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

    // PROBE: сигнатура будет уточнена по ошибке компиляции
    private fun probeSignature() {
        super.buildVideoRenderers()
    }

    companion object {
        val HW_MARKERS = listOf(".hw.", ".qcom.", ".qti.", ".exynos.", ".mtk.", "omx.qcom", "omx.sec", "c2.qti", "c2.qcom", "c2.exynos", "c2.mtk")

        val SOFTWARE_SELECTOR = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                .filter { info ->
                    val n = info.name.lowercase()
                    HW_MARKERS.none { n.contains(it) }
                }
        }
    }
}
