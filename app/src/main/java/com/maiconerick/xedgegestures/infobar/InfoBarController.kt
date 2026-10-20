package com.maiconerick.xedgegestures.infobar

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.R
import com.maiconerick.xedgegestures.data.prefs.StatType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent info bar overlay on the target display — visual match for xPTT's VitalsCard.
 * FPS / CPU / GPU / RAM use dim gray text labels; TEMP + BATTERY use tinted
 * vector icons in the accent color. Value colors follow xPTT thresholds
 * (amber warning, red saturation, green while charging).
 */
@Singleton
class InfoBarController @Inject constructor(
    @ApplicationContext private val appCtx: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var accentArgb: Int = 0xFF00E5FF.toInt()

    private var wm: WindowManager? = null
    private var displayContext: Context? = null
    private var view: LinearLayout? = null
    private var cells: Map<StatType, TextView> = emptyMap()
    private var batteryIcon: ImageView? = null
    private var loop: Job? = null
    private var stats: SysStats? = null
    private var enabledStats: Set<StatType> = emptySet()
    private var currentAccent: Int = 0
    private var manualHide = false

    fun applySettings(enabled: Boolean, statsToShow: Set<StatType>, accent: Int? = null) {
        val prevAccent = accentArgb
        accent?.let { accentArgb = it }
        val statsChanged = statsToShow != enabledStats
        val accentChanged = accent != null && prevAccent != accent
        enabledStats = statsToShow
        if (enabled && !manualHide) {
            if (view != null && (statsChanged || accentChanged)) stop()
            start()
        } else stop()
    }

    fun toggleManual() {
        manualHide = !manualHide
        if (manualHide) stop() else start()
    }

    private fun start() {
        main.post {
            if (view != null) return@post
            val target = TargetDisplay.resolve(appCtx) ?: return@post
            val dctx = appCtx.createDisplayContext(target)
            displayContext = dctx
            wm = dctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            stats = SysStats(appCtx)
            currentAccent = accentArgb

            val built = buildView(dctx, enabledStats, accentArgb)
            view = built.row
            cells = built.cells
            batteryIcon = built.batteryIcon

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(dctx, 6)
            }
            try { wm?.addView(built.row, lp) } catch (t: Throwable) {
                Log.e(TAG, "addView failed", t)
                return@post
            }

            loop = scope.launch {
                var tick = 0
                while (true) {
                    val snap = stats!!.sample(includeFps = tick % 2 == 0)
                    withContext(Dispatchers.Main) { applyValues(snap) }
                    tick++
                    delay(1000)
                }
            }
        }
    }

    private fun stop() {
        loop?.cancel(); loop = null
        FpsMeter.stop()
        main.post {
            val w = wm ?: return@post
            view?.let { runCatching { w.removeView(it) } }
            view = null; cells = emptyMap(); batteryIcon = null
            displayContext = null; wm = null; stats = null
        }
    }

    private fun applyValues(s: SysSnapshot) {
        cells[StatType.Fps]?.let {
            it.text = s.fps?.toString() ?: "–"
            it.setTextColor(fpsColor(s.fps, s.drops))
        }
        cells[StatType.Cpu]?.let {
            it.text = pctOf(s.cpuPct)
            it.setTextColor(loadColor(s.cpuPct))
        }
        cells[StatType.Gpu]?.let {
            it.text = pctOf(s.gpuPct)
            it.setTextColor(loadColor(s.gpuPct))
        }
        cells[StatType.Ram]?.let {
            it.text = ramOf(s)
            it.setTextColor(ramColor(s))
        }
        cells[StatType.Temp]?.let {
            it.text = s.tempC?.let { c -> "${c}°C" } ?: "–"
            it.setTextColor(tempColor(s.tempC))
        }
        cells[StatType.Battery]?.let {
            val pct = s.batteryPct?.let { p -> "$p%" } ?: "–"
            it.text = pct
            it.setTextColor(batteryColor(s))
        }
        // Swap the battery icon (charging vs plain) whenever charging state changes.
        batteryIcon?.let { iv ->
            val res = if (s.charging) R.drawable.ic_battery_charging else R.drawable.ic_battery
            val d = ContextCompat.getDrawable(iv.context, res)?.mutate()
            if (d != null) {
                DrawableCompat.setTint(d, if (s.charging) greenCharge else currentAccent)
                iv.setImageDrawable(d)
            }
        }
    }

    private data class Built(
        val row: LinearLayout,
        val cells: Map<StatType, TextView>,
        val batteryIcon: ImageView?,
    )

    private fun buildView(ctx: Context, wantStats: Set<StatType>, accent: Int): Built {
        val cellMap = mutableMapOf<StatType, TextView>()
        var batteryIv: ImageView? = null
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6))
            background = washBackground(ctx, accent)
        }

        val order = listOf(
            StatType.Fps, StatType.Cpu, StatType.Gpu, StatType.Ram,
            StatType.Temp, StatType.Battery,
        )
        var first = true
        order.forEach { kind ->
            if (kind !in wantStats) return@forEach
            if (!first) row.addView(spacer(ctx, dp(ctx, 12)))
            first = false

            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            when (kind) {
                StatType.Temp -> {
                    cell.addView(tintedIcon(ctx, R.drawable.ic_thermostat, accent))
                    cell.addView(spacer(ctx, dp(ctx, 5)))
                }
                StatType.Battery -> {
                    val iv = tintedIcon(ctx, R.drawable.ic_battery, accent)
                    batteryIv = iv
                    cell.addView(iv)
                    cell.addView(spacer(ctx, dp(ctx, 5)))
                }
                else -> {
                    val label = when (kind) {
                        StatType.Fps -> "FPS"; StatType.Cpu -> "CPU"
                        StatType.Gpu -> "GPU"; StatType.Ram -> "RAM"
                        else -> ""
                    }
                    cell.addView(TextView(ctx).apply {
                        text = label
                        setTextColor(Color.parseColor("#6A6A72"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    cell.addView(spacer(ctx, dp(ctx, 5)))
                }
            }

            val value = TextView(ctx).apply {
                text = "–"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                maxLines = 1
            }
            cell.addView(value)
            cellMap[kind] = value
            row.addView(cell)
        }
        return Built(row, cellMap, batteryIv)
    }

    private fun tintedIcon(ctx: Context, resId: Int, tintArgb: Int): ImageView {
        val iv = ImageView(ctx)
        val size = dp(ctx, 15)
        iv.layoutParams = LinearLayout.LayoutParams(size, size)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        val d = ContextCompat.getDrawable(ctx, resId)?.mutate()
        if (d != null) {
            DrawableCompat.setTint(d, tintArgb)
            DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
            iv.setImageDrawable(d)
        }
        return iv
    }

    private fun washBackground(ctx: Context, accent: Int): GradientDrawable {
        val darkCore = Color.parseColor("#0C0C0F")
        val washStart = (accent and 0x00FFFFFF) or 0x1A000000
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(washStart, darkCore, darkCore),
        ).apply {
            cornerRadius = dp(ctx, 14).toFloat()
            val stroke = (accent and 0x00FFFFFF) or (0x8C shl 24)
            setStroke(dp(ctx, 1), stroke)
        }
    }

    private fun spacer(ctx: Context, wPx: Int) = android.view.View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(wPx, 1)
    }

    private val amber = 0xFFFFB300.toInt()
    private val red = 0xFFFF3B4B.toInt()
    private val greenCharge = 0xFF3DDC84.toInt()

    private fun loadColor(pct: Int?): Int = when {
        pct == null -> Color.WHITE
        pct >= 95 -> red
        pct >= 85 -> amber
        else -> Color.WHITE
    }

    private fun fpsColor(fps: Int?, drops: Int): Int = when {
        drops >= 5 -> red
        drops >= 1 -> amber
        fps != null && fps < 25 -> amber
        else -> Color.WHITE
    }

    private fun tempColor(c: Int?): Int = when {
        c == null -> Color.WHITE
        c >= 85 -> red
        c >= 75 -> amber
        else -> Color.WHITE
    }

    private fun ramColor(s: SysSnapshot): Int {
        if (s.ramTotalMb <= 0) return Color.WHITE
        val used = s.ramUsedMb.toFloat() / s.ramTotalMb
        return when {
            used >= 0.90f -> red
            used >= 0.75f -> amber
            else -> Color.WHITE
        }
    }

    private fun batteryColor(s: SysSnapshot): Int {
        val p = s.batteryPct ?: return Color.WHITE
        return when {
            s.charging -> greenCharge
            p <= 15 -> red
            p <= 30 -> amber
            else -> Color.WHITE
        }
    }

    private fun pctOf(v: Int?): String = v?.let { "$it%" } ?: "–"
    private fun ramOf(s: SysSnapshot): String =
        if (s.ramTotalMb <= 0) "–"
        else "%.1f/%.0fG".format(s.ramUsedMb / 1024f, s.ramTotalMb / 1024f)

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    companion object { private const val TAG = "InfoBarController" }
}
