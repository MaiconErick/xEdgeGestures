package com.maiconerick.xedgegestures.actions

/** Short-swipe action fired when the finger leaves an edge before engaging a slider. */
sealed class Action {
    data object Back : Action()
    data object Home : Action()
    data object Recents : Action()
    data class LaunchApp(val component: String) : Action()
    data object KillPicker : Action()
    data object Screenshot : Action()
    data object ToggleInfoBar : Action()
    data object None : Action()
}

/** Hold-and-drag slider kind that engages after the finger crosses the engage distance. */
@kotlinx.serialization.Serializable
enum class SliderKind {
    None,
    MediaVolume,
    RingerVolume,
    BrightnessMainDisplay,
    BrightnessTargetDisplay,
}
