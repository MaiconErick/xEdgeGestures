package com.maiconerick.xedgegestures.infobar

import com.maiconerick.xedgegestures.root.RootShell

/**
 * Ported from xPlayTimeTracker's FpsMeter. TimeStats is off by default and
 * expensive to keep on, so we enable-clear-dump-clear per sample and rely on
 * a longer polling interval.
 */
object FpsMeter {

    private const val SF = "dumpsys SurfaceFlinger --timestats"
    private val IGNORE = listOf(
        "com.maiconerick.xedgegestures", "StatusBar", "NavigationBar", "Wallpaper",
        "com.android.systemui", "Dim layer", "ScreenDecor", "InputMethod", "Splash",
    )

    @Volatile private var enabled = false

    fun start() {
        if (enabled) return
        enabled = RootShell.exec("$SF -enable; $SF -clear")
    }

    fun stop() {
        if (!enabled) return
        RootShell.exec("$SF -disable; $SF -clear")
        enabled = false
    }

    data class Reading(val fps: Int, val dropped: Int)

    fun sample(): Reading? {
        if (!enabled) { start(); if (!enabled) return null }
        val dump = RootShell.capture("$SF -dump -maxlayers 8; $SF -clear") ?: return null
        if (!dump.contains("layerName")) { enabled = false; return null }

        var name: String? = null
        var frames = 0
        var pending: Double? = null
        var dropped = 0
        var best: Reading? = null
        var bestFrames = 0

        fun consider() {
            val n = name ?: return
            val fps = pending ?: return
            val r = Reading(Math.round(fps).toInt(), dropped)
            if (frames > bestFrames && IGNORE.none { n.contains(it, ignoreCase = true) }) {
                bestFrames = frames; best = r
            }
        }

        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("layerName") -> {
                    consider()
                    name = line.substringAfter("=").trim()
                    frames = 0; pending = null; dropped = 0
                }
                line.startsWith("totalFrames") ->
                    frames = line.substringAfter("=").trim().toIntOrNull() ?: 0
                line.startsWith("droppedFrames") ->
                    dropped = line.substringAfter("=").trim().toIntOrNull() ?: 0
                line.startsWith("averageFPS") ->
                    pending = line.substringAfter("=").trim().toDoubleOrNull()
            }
        }
        consider()
        return best?.takeIf { bestFrames >= 5 && it.fps > 0 }
    }
}
