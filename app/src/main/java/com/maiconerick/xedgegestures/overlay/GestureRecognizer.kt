package com.maiconerick.xedgegestures.overlay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Three-outcome gesture state machine per edge.
 *
 *   short swipe:  release with inward >= swipeThresholdPx, never engaged/held.
 *   hold-drag:    inward past bar + peak-decay + perpendicular activity.
 *                 Once engaged, the finger's ABSOLUTE position along the
 *                 perpendicular axis maps to a 0..1 percentage that the slider
 *                 consumes directly (no delta tracking). Screen edges saturate
 *                 to 0/100 with a configurable padding "dead zone" so both
 *                 extremes are reachable without touching the bezel.
 *   long swipe:   inward past swipeThresholdPx + held still ~holdMs.
 */
class GestureRecognizer(
    private val edge: Edge,
    private val swipeThresholdPx: Float,
    private val engageInwardMinPx: Float,
    private val engagePerpMinPx: Float,
    private val engagePeakDecayMs: Long,
    private val engageInwardBackoffPx: Float,
    private val holdMs: Long,
    private val holdMovementSlopPx: Float,
    private val perpendicularAxisLengthPx: Float,
    private val perpendicularPaddingPx: Float,
    private val onEngage: () -> Unit,
    private val onDragPercent: (Float) -> Unit,
    private val onCommit: () -> Unit,
    private val onShortSwipe: () -> Unit,
    private val onLongSwipe: () -> Unit,
) {

    private enum class State { Idle, Tracking, Engaged, Held }

    private var state = State.Idle
    private var startX = 0f
    private var startY = 0f
    private var peakInward = 0f
    private var peakInwardAt = 0L
    private var holdArmedAt = 0L
    private var lastX = 0f
    private var lastY = 0f
    private val main = Handler(Looper.getMainLooper())
    private val holdRunnable = Runnable {
        if (state == State.Tracking) {
            state = State.Held
            onLongSwipe()
        }
    }

    fun onTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                state = State.Tracking
                startX = e.rawX; startY = e.rawY
                lastX = startX; lastY = startY
                peakInward = 0f
                peakInwardAt = e.eventTime
                holdArmedAt = 0
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - startX
                val dy = e.rawY - startY
                val inward = inward(dx, dy)
                val perpFromStart = perpendicular(dx, dy)

                if (inward > peakInward) {
                    peakInward = inward
                    peakInwardAt = e.eventTime
                }

                if (state == State.Tracking && peakInward >= engageInwardMinPx) {
                    val stoppedGoingIn = (e.eventTime - peakInwardAt) >= engagePeakDecayMs ||
                        (peakInward - inward) >= engageInwardBackoffPx
                    if (stoppedGoingIn && abs(perpFromStart) >= engagePerpMinPx) {
                        state = State.Engaged
                        cancelHold()
                        onEngage()
                    }
                }
                if (state == State.Engaged) {
                    onDragPercent(absolutePercent(e))
                    return true
                }

                if (state == State.Tracking && inward >= swipeThresholdPx) {
                    if (holdArmedAt == 0L) {
                        holdArmedAt = e.eventTime
                        lastX = e.rawX; lastY = e.rawY
                        main.postDelayed(holdRunnable, holdMs)
                    } else {
                        val movedX = abs(e.rawX - lastX)
                        val movedY = abs(e.rawY - lastY)
                        if (movedX > holdMovementSlopPx || movedY > holdMovementSlopPx) {
                            cancelHold()
                            holdArmedAt = e.eventTime
                            lastX = e.rawX; lastY = e.rawY
                            main.postDelayed(holdRunnable, holdMs)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.rawX - startX
                val dy = e.rawY - startY
                val inward = inward(dx, dy)
                val wasEngaged = state == State.Engaged
                val wasHeld = state == State.Held
                cancelHold()
                state = State.Idle
                if (wasEngaged) onCommit()
                else if (wasHeld) { /* long swipe already fired */ }
                else if (inward >= swipeThresholdPx) onShortSwipe()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                state = State.Idle
                return true
            }
        }
        return false
    }

    private fun cancelHold() {
        main.removeCallbacks(holdRunnable)
        holdArmedAt = 0
    }

    private fun inward(dx: Float, dy: Float): Float = when (edge) {
        Edge.Right -> -dx
        Edge.Left -> dx
        Edge.Bottom -> -dy
        Edge.Top -> dy
    }

    private fun perpendicular(dx: Float, dy: Float): Float = when (edge) {
        Edge.Right, Edge.Left -> -dy
        Edge.Bottom, Edge.Top -> dx
    }

    /** 0.0..1.0 along the perpendicular axis, with padding + saturation. */
    private fun absolutePercent(e: MotionEvent): Float {
        val usable = (perpendicularAxisLengthPx - 2 * perpendicularPaddingPx).coerceAtLeast(1f)
        return when (edge) {
            Edge.Right, Edge.Left -> {
                // Y grows downward; top = 100%, bottom = 0%.
                val yUsable = (e.rawY - perpendicularPaddingPx).coerceIn(0f, usable)
                1f - (yUsable / usable)
            }
            Edge.Bottom, Edge.Top -> {
                // Left = 0%, right = 100%.
                val xUsable = (e.rawX - perpendicularPaddingPx).coerceIn(0f, usable)
                xUsable / usable
            }
        }
    }
}
