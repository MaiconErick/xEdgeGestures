package com.maiconerick.xedgegestures.slider

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SliderIcon { Brightness, Volume }

/**
 * Center-screen readout on the target display — a fixed 140dp square with rounded neon
 * border. Icon on top (accent-tinted), big monospace percent below. Value
 * updates snap immediately (no tween). Auto-hides ~700ms after last update.
 */
@Singleton
class SliderIndicator @Inject constructor(
    @ApplicationContext private val appCtx: Context,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile var accentArgb: Int = 0xFF00E5FF.toInt()

    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var iconView: ImageView? = null
    private var valueText: TextView? = null
    private var currentIcon: SliderIcon? = null
    private val hideRunnable = Runnable { removeView() }

    fun show(icon: SliderIcon, pct: Int) {
        main.post {
            if (root == null && !ensureView()) return@post
            if (icon != currentIcon) {
                currentIcon = icon
                iconView?.let { applyIcon(it, icon, accentArgb) }
            }
            valueText?.text = "${pct.coerceIn(0, 100)}%"
            scheduleHide()
        }
    }

    fun hide() { main.post { removeView() } }

    private fun ensureView(): Boolean {
        val target = TargetDisplay.resolve(appCtx) ?: return false
        val dctx = appCtx.createDisplayContext(target)
        wm = dctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val square = FrameLayout(dctx).apply {
            background = solidBackground(dctx, accentArgb)
        }
        val col = LinearLayout(dctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val icon = ImageView(dctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(dctx, 44), dp(dctx, 44))
        }
        val value = TextView(dctx).apply {
            text = "0%"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(dctx, 6) }
        }
        col.addView(icon)
        col.addView(value)
        square.addView(col)

        root = square
        iconView = icon
        valueText = value
        currentIcon = null

        // Fixed square: forcing the WM window size guarantees the overlay is a
        // proper square regardless of the child view's intrinsic dimensions.
        val side = dp(dctx, 120)
        val lp = WindowManager.LayoutParams(
            side, side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        return try { wm?.addView(square, lp); true } catch (_: Throwable) {
            root = null; iconView = null; valueText = null; wm = null
            false
        }
    }

    private fun applyIcon(iv: ImageView, kind: SliderIcon, tint: Int) {
        val res = when (kind) {
            SliderIcon.Brightness -> R.drawable.ic_brightness
            SliderIcon.Volume -> R.drawable.ic_volume
        }
        val d = ContextCompat.getDrawable(iv.context, res)?.mutate() ?: return
        DrawableCompat.setTint(d, tint)
        DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
        iv.setImageDrawable(d)
    }

    private fun scheduleHide() {
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun removeView() {
        main.removeCallbacks(hideRunnable)
        val w = wm ?: return
        root?.let { runCatching { w.removeView(it) } }
        root = null; iconView = null; valueText = null; wm = null
        currentIcon = null
    }

    private fun solidBackground(ctx: Context, accent: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#0C0C0F"))
            cornerRadius = dp(ctx, 22).toFloat()
            val stroke = (accent and 0x00FFFFFF) or (0x99 shl 24)
            setStroke(dp(ctx, 1), stroke)
        }
    }

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    companion object { private const val HIDE_DELAY_MS = 700L }
}
