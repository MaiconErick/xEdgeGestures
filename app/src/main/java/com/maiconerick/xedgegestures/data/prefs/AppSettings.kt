package com.maiconerick.xedgegestures.data.prefs

import com.maiconerick.xedgegestures.actions.SliderKind
import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    None, Back, Home, Recents, LaunchApp, KillPicker, Screenshot, ToggleInfoBar;
}

@Serializable
enum class StatType { Fps, Battery, Cpu, Ram, Temp, Gpu }

/**
 * One touch zone on an edge. An edge can hold several (e.g. left / middle / right thirds of
 * the top edge), each with its own actions and preview color.
 */
@Serializable
data class EdgeTrigger(
    val id: String = java.util.UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    /** Zone start along the edge, % of its length (top→bottom or left→right). */
    val startPct: Float = 0f,
    /** Zone end along the edge, % of its length. */
    val endPct: Float = 100f,
    /** Thickness: width on right/left edges, height on top/bottom edges. */
    val sizeDp: Int = 30,
    /** Preview-tint color (opaque ARGB); drawn translucent over the screen. */
    val colorArgb: Long = TriggerPalette.first(),
    val shortSwipe: ActionType = ActionType.None,
    val shortSwipeArg: String = "",
    val longSwipe: ActionType = ActionType.None,
    val longSwipeArg: String = "",
    val slider: SliderKind = SliderKind.None,
)

/** Neon colors handed out to new triggers so neighbors are easy to tell apart. */
val TriggerPalette: List<Long> = listOf(
    0xFFFF2E9A, 0xFF00E5FF, 0xFF39FF88, 0xFFB14BFF, 0xFFFFE14B,
    0xFFFF8A1F, 0xFFFF3B3B, 0xFF3D7BFF, 0xFFB6FF00, 0xFFF0F0F0,
)

@Serializable
data class EdgeConfig(
    val enabled: Boolean = false,
    /** Touch zones on this edge. Empty only in settings saved before triggers existed. */
    val triggers: List<EdgeTrigger> = emptyList(),
    // ---- Legacy single-zone fields: read once by the migration into [triggers]. ----
    /** Width of vertical edges (right/left) OR height of horizontal edges (top/bottom). */
    val sizeDp: Int = 30,
    /** Length in dp along the edge; 0 = full edge. */
    val lengthDp: Int = 0,
    val shortSwipe: ActionType = ActionType.None,
    val shortSwipeArg: String = "",
    /** Fires when the finger swipes inward past the short-swipe threshold then holds still. */
    val longSwipe: ActionType = ActionType.None,
    val longSwipeArg: String = "",
    val slider: SliderKind = SliderKind.None,
)

@Serializable
data class AppSettings(
    val enabled: Boolean = true,
    val debugTint: Boolean = false,
    val showVolumeSystemUi: Boolean = true,
    val accentName: String = "CYAN",
    val homeAppComponent: String = DEFAULT_HOME,
    val rightEdge: EdgeConfig = EdgeConfig(true, sizeDp = 30, shortSwipe = ActionType.Back, slider = SliderKind.MediaVolume),
    val bottomEdge: EdgeConfig = EdgeConfig(true, sizeDp = 30, shortSwipe = ActionType.Home),
    val leftEdge: EdgeConfig = EdgeConfig(false, sizeDp = 30),
    val topEdge: EdgeConfig = EdgeConfig(false, sizeDp = 30),
    val infoBarEnabled: Boolean = false,
    val infoBarStats: Set<StatType> = setOf(StatType.Fps, StatType.Battery, StatType.Temp),
    /** Floor for Display 4 brightness, expressed as % of hardware max (0..30). */
    val targetDisplayMinBrightnessPct: Int = 2,
    /**
     * Distance from screen edge (dp) at which slider input saturates to 0% or 100%.
     * Ensures both extremes are reachable without your finger having to touch the
     * physical bezel.
     */
    val sliderEdgePaddingDp: Int = 40,
) {
    fun edge(e: com.maiconerick.xedgegestures.overlay.Edge): EdgeConfig = when (e) {
        com.maiconerick.xedgegestures.overlay.Edge.Right -> rightEdge
        com.maiconerick.xedgegestures.overlay.Edge.Left -> leftEdge
        com.maiconerick.xedgegestures.overlay.Edge.Bottom -> bottomEdge
        com.maiconerick.xedgegestures.overlay.Edge.Top -> topEdge
    }

    fun withEdge(e: com.maiconerick.xedgegestures.overlay.Edge, cfg: EdgeConfig): AppSettings = when (e) {
        com.maiconerick.xedgegestures.overlay.Edge.Right -> copy(rightEdge = cfg)
        com.maiconerick.xedgegestures.overlay.Edge.Left -> copy(leftEdge = cfg)
        com.maiconerick.xedgegestures.overlay.Edge.Bottom -> copy(bottomEdge = cfg)
        com.maiconerick.xedgegestures.overlay.Edge.Top -> copy(topEdge = cfg)
    }

    companion object {
        /** Empty = fire the system's HOME intent (whichever launcher is default on Display 4). */
        const val DEFAULT_HOME = ""
    }
}
