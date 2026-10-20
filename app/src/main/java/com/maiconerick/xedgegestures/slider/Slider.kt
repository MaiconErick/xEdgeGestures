package com.maiconerick.xedgegestures.slider

/**
 * A continuous control driven by a hold-and-drag gesture. The recognizer maps
 * the finger's absolute position along the perpendicular axis (accounting for
 * user-configured edge padding) to a 0.0..1.0 percentage; sliders just clamp
 * hardware/software values to their own range from that.
 */
interface Slider {
    fun onEngage()
    fun onDrag(percent: Float)   // 0.0..1.0
    fun onCommit()
}
