package com.maiconerick.xedgegestures.slider

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

class VolumeSlider(
    private val context: Context,
    private val stream: Int = AudioManager.STREAM_MUSIC,
    private val showSystemUi: Boolean = true,
    private val indicator: SliderIndicator? = null,
) : Slider {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var lastSent = -1

    override fun onEngage() {
        if (!showSystemUi) reportIndicator(am.getStreamVolume(stream))
    }

    override fun onDrag(percent: Float) {
        val max = am.getStreamMaxVolume(stream)
        val target = (max * percent.coerceIn(0f, 1f)).roundToInt().coerceIn(0, max)
        if (target != lastSent) {
            lastSent = target
            val flag = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0
            am.setStreamVolume(stream, target, flag)
        }
        if (!showSystemUi) reportIndicator(target)
    }

    override fun onCommit() {}

    private fun reportIndicator(v: Int) {
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        val pct = (100 * v) / max
        indicator?.show(SliderIcon.Volume, pct)
    }
}
