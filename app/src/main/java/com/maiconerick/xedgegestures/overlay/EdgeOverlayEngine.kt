package com.maiconerick.xedgegestures.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.maiconerick.xedgegestures.Config
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.actions.Action
import com.maiconerick.xedgegestures.actions.ActionExecutor
import com.maiconerick.xedgegestures.actions.SliderKind
import com.maiconerick.xedgegestures.data.prefs.AppSettings
import com.maiconerick.xedgegestures.data.prefs.EdgeTrigger
import android.hardware.display.DisplayManager
import com.maiconerick.xedgegestures.slider.MainBrightnessSlider
import com.maiconerick.xedgegestures.slider.Slider
import com.maiconerick.xedgegestures.slider.SliderIndicator
import com.maiconerick.xedgegestures.slider.TargetDisplayBrightnessSlider
import com.maiconerick.xedgegestures.slider.VolumeSlider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeOverlayEngine @Inject constructor(
    private val executor: ActionExecutor,
    private val indicator: SliderIndicator,
) {

    private var wm: WindowManager? = null
    private var displayContext: Context? = null
    private var appCtx: Context? = null
    private val mounted = mutableListOf<View>()
    private var vibrator: Vibrator? = null
    private var current: AppSettings = AppSettings()
    private val main = Handler(Looper.getMainLooper())

    fun apply(appContext: Context, settings: AppSettings) {
        val ctx = appContext.applicationContext
        appCtx = ctx
        executor.homeComponent = settings.homeAppComponent
        executor.showVolumeSystemUi = settings.showVolumeSystemUi

        val target = TargetDisplay.resolve(ctx)
        if (target == null) {
            Log.w(TAG, "No display found")
            tearDown()
            return
        }
        if (displayContext == null) {
            displayContext = ctx.createDisplayContext(target)
            wm = displayContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            vibrator = resolveVibrator(ctx)
        }
        current = settings

        main.post {
            removeAllInternal()
            if (!settings.enabled) return@post
            val dctx = displayContext ?: return@post
            for (edge in Edge.entries) {
                val cfg = settings.edge(edge)
                if (!cfg.enabled) continue
                cfg.triggers.filter { it.enabled }.forEach { mountTrigger(dctx, edge, it) }
            }
        }
        watchDisplay(ctx)
    }

    // Zones are placed in pixels, so re-layout when the target display changes size/rotation.
    private var displayListener: DisplayManager.DisplayListener? = null
    private var lastSize: Pair<Int, Int>? = null

    private fun watchDisplay(ctx: Context) {
        if (displayListener != null) return
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) {}
            override fun onDisplayRemoved(id: Int) {}
            override fun onDisplayChanged(id: Int) {
                if (id != TargetDisplay.id) return
                val c = appCtx ?: return
                val size = displaySize()
                if (size != lastSize) { lastSize = size; apply(c, current) }
            }
        }
        dm.registerDisplayListener(l, main)
        displayListener = l
    }

    /** Full display size in px (not the app area), which is what edge zones span. */
    private fun displaySize(): Pair<Int, Int>? {
        val w = wm ?: return null
        return if (Build.VERSION.SDK_INT >= 30) {
            w.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") w.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    fun tearDown() {
        main.post { removeAllInternal() }
        appCtx?.let { c ->
            displayListener?.let { (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it) }
        }
        displayListener = null
        lastSize = null
        displayContext = null
        wm = null
        appCtx = null
        vibrator = null
    }

    private fun removeAllInternal() {
        val w = wm ?: return
        mounted.forEach { runCatching { w.removeView(it) } }
        mounted.clear()
    }

    private fun mountTrigger(dctx: Context, edge: Edge, cfg: EdgeTrigger) {
        val vertical = edge == Edge.Right || edge == Edge.Left
        val density = dctx.resources.displayMetrics.density
        val edgeSizePx = (cfg.sizeDp * density).toInt()
        val (dispW, dispH) = displaySize() ?: return
        if (lastSize == null) lastSize = dispW to dispH
        val axisPx = if (vertical) dispH else dispW
        val full = cfg.startPct <= 0f && cfg.endPct >= 100f
        val offsetPx = (cfg.startPct / 100f * axisPx).toInt()
        val edgeLengthPx = if (full) WindowManager.LayoutParams.MATCH_PARENT
            else ((cfg.endPct - cfg.startPct) / 100f * axisPx).toInt().coerceAtLeast(1)
        val gravity = when (edge) {
            Edge.Right -> Gravity.RIGHT or Gravity.TOP
            Edge.Left -> Gravity.LEFT or Gravity.TOP
            Edge.Top -> Gravity.TOP or Gravity.LEFT
            Edge.Bottom -> Gravity.BOTTOM or Gravity.LEFT
        }
        val thresholdPx = Config.SWIPE_THRESHOLD_DP * density
        val engageInwardMinPx = (cfg.sizeDp + Config.ENGAGE_INWARD_EXTRA_DP) * density
        val engagePerpMinPx = Config.ENGAGE_PERP_MIN_DP * density
        val engageBackoffPx = Config.ENGAGE_INWARD_BACKOFF_DP * density
        val holdSlopPx = Config.HOLD_MOVEMENT_SLOP_DP * density
        val paddingPx = current.sliderEdgePaddingDp * density

        // Perpendicular axis length: screen height for vertical edges, width for horizontal.
        val metrics = dctx.resources.displayMetrics
        val perpAxisPx = when (edge) {
            Edge.Right, Edge.Left -> metrics.heightPixels.toFloat()
            Edge.Top, Edge.Bottom -> metrics.widthPixels.toFloat()
        }

        val slider = sliderFor(cfg.slider, dctx.applicationContext)

        val recognizer = GestureRecognizer(
            edge = edge,
            swipeThresholdPx = thresholdPx,
            engageInwardMinPx = engageInwardMinPx,
            engagePerpMinPx = engagePerpMinPx,
            engagePeakDecayMs = Config.ENGAGE_PEAK_DECAY_MS,
            engageInwardBackoffPx = engageBackoffPx,
            holdMs = Config.HOLD_DWELL_MS,
            holdMovementSlopPx = holdSlopPx,
            perpendicularAxisLengthPx = perpAxisPx,
            perpendicularPaddingPx = paddingPx,
            onEngage = {
                haptic(Config.HAPTIC_ENGAGE_MS)
                slider?.onEngage()
            },
            onDragPercent = { pct -> slider?.onDrag(pct) },
            onCommit = { slider?.onCommit() },
            onShortSwipe = {
                haptic(Config.HAPTIC_SHORT_MS)
                executor.execute(executor.fromConfig(cfg.shortSwipe, cfg.shortSwipeArg))
            },
            onLongSwipe = {
                haptic(Config.HAPTIC_HOLD_MS)
                executor.execute(executor.fromConfig(cfg.longSwipe, cfg.longSwipeArg))
            },
        )

        val view = FrameLayout(dctx).apply {
            setBackgroundColor(if (current.debugTint) previewTint(cfg.colorArgb) else Color.TRANSPARENT)
            setOnTouchListener(EdgeGestureListener(recognizer))
        }
        val lp = WindowManager.LayoutParams(
            if (vertical) edgeSizePx else edgeLengthPx,
            if (vertical) edgeLengthPx else edgeSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            if (!full) { if (vertical) y = offsetPx else x = offsetPx }
        }
        try {
            wm?.addView(view, lp)
            mounted += view
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed for $edge", t)
        }
    }

    private fun sliderFor(kind: SliderKind, appCtx: Context): Slider? = when (kind) {
        SliderKind.None -> null
        SliderKind.MediaVolume -> VolumeSlider(
            appCtx,
            showSystemUi = current.showVolumeSystemUi,
            indicator = if (!current.showVolumeSystemUi) indicator else null,
        )
        SliderKind.RingerVolume -> VolumeSlider(
            appCtx, stream = android.media.AudioManager.STREAM_RING,
            showSystemUi = current.showVolumeSystemUi,
            indicator = if (!current.showVolumeSystemUi) indicator else null,
        )
        SliderKind.BrightnessMainDisplay -> MainBrightnessSlider(appCtx, indicator)
        // Single-screen devices have no second panel; drive the main display instead.
        SliderKind.BrightnessTargetDisplay -> if (TargetDisplay.isSecondary) {
            TargetDisplayBrightnessSlider(
                minPctOfMax = current.targetDisplayMinBrightnessPct,
                indicator = indicator,
            )
        } else MainBrightnessSlider(appCtx, indicator)
    }

    /** The trigger's color at ~40% alpha so the app underneath stays visible. */
    private fun previewTint(argb: Long): Int = ((argb.toInt() and 0x00FFFFFF) or 0x66000000)

    private fun haptic(ms: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Throwable) {}
    }

    private fun resolveVibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    companion object { private const val TAG = "EdgeOverlayEngine" }
}
