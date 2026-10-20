package com.maiconerick.xedgegestures.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drifting neon starfield (ported from xSiralimCompanion): soft-glowing dots
 * slowly floating in random directions, wrapping at edges, with a subtle
 * radius pulse. Pure black behind for AMOLED; dots follow the accent color.
 */
private data class Star(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val baseR: Float,
    val hue: Color,
    val phase: Float,
)

/** ~30 updates/second. */
private const val FRAME_MS = 33L

@Composable
fun StarfieldBackground(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val neon = LocalNeon.current
    val stars = remember(neon) { mutableListOf<Star>() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    var step by remember { mutableStateOf(2f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    /*
     * Capped at ~30 fps instead of every display frame: an animated full-screen
     * background forces the whole UI to be re-rasterized on each update, so this
     * halves the cost. Driven by delay() rather than withFrameMillis so we don't
     * wake for frames we would only throw away, and RESUMED-scoped so a paused
     * window animates nothing. `step` keeps the drift speed wall-clock based, so
     * the dots move at the same visible rate as they did at 60 fps.
     */
    LaunchedEffect(lifecycle, enabled) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = System.currentTimeMillis()
            while (true) {
                delay(FRAME_MS)
                val now = System.currentTimeMillis()
                step = ((now - last) / 16.67f).coerceIn(0.5f, 6f)
                last = now
                tick = now
            }
        }
    }
    Box(modifier = modifier.background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // read `enabled` inside the draw scope so Compose invalidates the
            // canvas the moment the toggle flips — skipping stars = plain black.
            if (!enabled) return@Canvas
            if (stars.isEmpty()) {
                val palette = listOf(neon.color, neon.color, neon.dim, Color(0xFFFFFFFF))
                repeat(90) {
                    stars += Star(
                        x = Random.nextFloat() * size.width,
                        y = Random.nextFloat() * size.height,
                        vx = (Random.nextFloat() - 0.5f) * 0.9f,
                        vy = (Random.nextFloat() - 0.5f) * 0.9f,
                        baseR = 0.8f + Random.nextFloat() * 2.2f,
                        hue = palette.random(),
                        phase = Random.nextFloat() * 6.283f,
                    )
                }
            }
            tick.let { /* subscribe to redraw */ }
            val t = tick / 1000f
            stars.forEach { s ->
                s.x += s.vx * step
                s.y += s.vy * step
                if (s.x < 0f) s.x += size.width else if (s.x > size.width) s.x -= size.width
                if (s.y < 0f) s.y += size.height else if (s.y > size.height) s.y -= size.height
                val pulse = 0.6f + 0.4f * ((sin(t * 1.8f + s.phase) + 1f) * 0.5f)
                val r = s.baseR * (0.6f + 0.4f * pulse)
                // soft glow: draw larger dim circle behind + bright core
                drawCircle(color = s.hue.copy(alpha = 0.10f * pulse), radius = r * 4f, center = Offset(s.x, s.y))
                drawCircle(color = s.hue.copy(alpha = 0.9f * pulse), radius = r, center = Offset(s.x, s.y))
            }
        }
    }
}
