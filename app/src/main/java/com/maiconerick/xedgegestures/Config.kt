package com.maiconerick.xedgegestures

object Config {
    const val TARGET_DISPLAY_ID = 4
    const val SWIPE_THRESHOLD_DP = 100
    /** Inward distance past the bar's edge before slider mode is even considered. */
    const val ENGAGE_INWARD_EXTRA_DP = 20
    /** Perpendicular movement required to engage — kept small, the real gate is the peak-decay check. */
    const val ENGAGE_PERP_MIN_DP = 15
    /** Peak inward must stop growing for this long before slider mode can engage. */
    const val ENGAGE_PEAK_DECAY_MS = 90L
    /** Or: peak inward must have backed off by this much (finger recoiled). */
    const val ENGAGE_INWARD_BACKOFF_DP = 12
    const val DRAG_PX_PER_VOL_STEP_DP = 20
    const val DRAG_PX_PER_BRIGHTNESS_RANGE_DP = 260
    const val HAPTIC_SHORT_MS = 20L
    const val HAPTIC_ENGAGE_MS = 30L
    const val HAPTIC_HOLD_MS = 45L
    const val HOLD_DWELL_MS = 380L
    const val HOLD_MOVEMENT_SLOP_DP = 12
}
