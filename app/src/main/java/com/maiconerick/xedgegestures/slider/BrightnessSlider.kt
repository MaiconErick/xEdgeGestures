package com.maiconerick.xedgegestures.slider

import android.content.Context
import android.provider.Settings
import com.maiconerick.xedgegestures.root.RootShell
import kotlin.math.roundToInt

/** Main-display brightness, linear map from percent to Settings.System.SCREEN_BRIGHTNESS. */
class MainBrightnessSlider(
    private val context: Context,
    private val indicator: SliderIndicator? = null,
) : Slider {

    private val minHw = 3
    private val maxHw = 255
    private var lastSent = -1

    override fun onEngage() {
        val current = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Throwable) { 128 }
        indicator?.show(SliderIcon.Brightness, hwToPct(current))
    }

    override fun onDrag(percent: Float) {
        val p = percent.coerceIn(0f, 1f)
        val target = minHw + ((maxHw - minHw) * p).roundToInt()
        if (target == lastSent) return
        lastSent = target
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
            } else {
                RootShell.exec("settings put system screen_brightness $target")
            }
        } catch (_: Throwable) {}
        indicator?.show(SliderIcon.Brightness, (p * 100).roundToInt())
    }

    override fun onCommit() {}

    private fun hwToPct(hw: Int): Int {
        val range = (maxHw - minHw).coerceAtLeast(1)
        return (((hw - minHw).coerceAtLeast(0) * 100) / range).coerceIn(0, 100)
    }
}

/** Display 4 brightness, linear, min floor configurable in settings. */
class TargetDisplayBrightnessSlider(
    private val minPctOfMax: Int,
    private val indicator: SliderIndicator? = null,
) : Slider {

    private var maxHw = DEFAULT_MAX_HW
    private var minHw = DEFAULT_MAX_HW * minPctOfMax / 100
    private var lastSent = -1

    override fun onEngage() {
        maxHw = RootShell.capture("cat $NODE_MAX")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            ?: DEFAULT_MAX_HW
        minHw = (maxHw * minPctOfMax / 100).coerceAtLeast(1)
        val currentHw = RootShell.capture("cat $NODE_VALUE")?.trim()?.toIntOrNull() ?: (maxHw / 2)
        indicator?.show(SliderIcon.Brightness, hwToPct(currentHw))
    }

    override fun onDrag(percent: Float) {
        val p = percent.coerceIn(0f, 1f)
        val target = minHw + ((maxHw - minHw) * p).roundToInt()
        if (target == lastSent) return
        lastSent = target
        RootShell.exec("echo $target > $NODE_VALUE")
        indicator?.show(SliderIcon.Brightness, (p * 100).roundToInt())
    }

    override fun onCommit() {}

    private fun hwToPct(hw: Int): Int {
        val range = (maxHw - minHw).coerceAtLeast(1)
        return (((hw - minHw).coerceAtLeast(0) * 100) / range).coerceIn(0, 100)
    }

    companion object {
        private const val NODE_VALUE = "/sys/class/backlight/panel1-backlight/brightness"
        private const val NODE_MAX = "/sys/class/backlight/panel1-backlight/max_brightness"
        private const val DEFAULT_MAX_HW = 4095
    }
}
