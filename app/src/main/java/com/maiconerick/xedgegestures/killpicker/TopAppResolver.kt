package com.maiconerick.xedgegestures.killpicker

import android.content.ComponentName
import android.util.Log
import com.maiconerick.xedgegestures.root.RootShell
import javax.inject.Inject
import javax.inject.Singleton

data class TopApp(val displayId: Int, val component: ComponentName?)

/**
 * Best-effort resolver for "what is currently on top of each display".
 * Handles multiple dumpsys shapes since the exact schema shifts between
 * Android versions and OEM builds.
 */
@Singleton
class TopAppResolver @Inject constructor() {

    private val display = Regex("Display #(\\d+)")
    private val component = Regex("([A-Za-z][\\w.]+)/([\\w.$]+)")

    /**
     * Topmost activity per display, skipping [selfPkg]. The picker itself is launched on
     * the secondary display, so it is always that display's resumed activity; walking the
     * "activities from top to bottom" list and taking the first foreign ActivityRecord
     * yields the app that sits right under it.
     */
    fun topPerDisplay(displays: List<Int>, selfPkg: String): Map<Int, ComponentName?> {
        val dump = RootShell.capture("dumpsys activity activities") ?: run {
            Log.w(TAG, "dumpsys returned null")
            return displays.associateWith<Int, ComponentName?> { null }
        }
        val out = displays.associateWith<Int, ComponentName?> { null }.toMutableMap()

        var currentDisplay: Int? = null
        val found = mutableSetOf<Int>()
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("Display #")) {
                currentDisplay = display.find(line)?.groupValues?.get(1)?.toIntOrNull()
                continue
            }
            // The per-display sections end where the global summary starts.
            if (line.startsWith("ResumedActivity") || line.startsWith("mFocusedApp")) break
            val disp = currentDisplay ?: continue
            if (disp !in out || disp in found) continue
            if (!line.contains("ActivityRecord{")) continue
            val m = component.find(line.substringAfter("ActivityRecord{")) ?: continue
            val pkg = m.groupValues[1]
            if (pkg == selfPkg) continue
            val cls = m.groupValues[2].let { if (it.startsWith(".")) pkg + it else it }
            out[disp] = ComponentName(pkg, cls)
            found.add(disp)
        }
        Log.i(TAG, "topPerDisplay ${out.mapValues { it.value?.flattenToShortString() }}")
        return out
    }

    fun forceStop(pkg: String): Boolean = RootShell.exec("am force-stop $pkg")

    companion object { private const val TAG = "xEdgeTopAppResolver" }
}
