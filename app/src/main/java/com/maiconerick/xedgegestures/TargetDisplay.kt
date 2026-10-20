package com.maiconerick.xedgegestures

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Display the overlays live on: the Thor's second screen ([Config.TARGET_DISPLAY_ID])
 * when it exists, otherwise the main display — so single-screen devices work too.
 */
object TargetDisplay {

    @Volatile var id: Int = Display.DEFAULT_DISPLAY
        private set

    /** True when running on a dual-screen device and targeting the second screen. */
    val isSecondary: Boolean get() = id != Display.DEFAULT_DISPLAY

    fun resolve(ctx: Context): Display? {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Config.TARGET_DISPLAY_ID) ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
        id = d?.displayId ?: Display.DEFAULT_DISPLAY
        return d
    }
}
