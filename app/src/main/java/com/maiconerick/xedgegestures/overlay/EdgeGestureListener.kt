package com.maiconerick.xedgegestures.overlay

import android.view.MotionEvent
import android.view.View

/** Thin View.OnTouchListener adapter around a per-edge [GestureRecognizer]. */
class EdgeGestureListener(
    private val recognizer: GestureRecognizer,
) : View.OnTouchListener {
    override fun onTouch(v: View, e: MotionEvent): Boolean = recognizer.onTouch(e)
}

enum class Edge { Right, Left, Bottom, Top }
