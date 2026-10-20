package com.maiconerick.xedgegestures.ui.settings

import android.hardware.display.DisplayManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maiconerick.xedgegestures.Config
import com.maiconerick.xedgegestures.data.prefs.AppSettings
import com.maiconerick.xedgegestures.data.prefs.EdgeTrigger
import com.maiconerick.xedgegestures.data.prefs.TriggerPalette
import com.maiconerick.xedgegestures.overlay.Edge
import com.maiconerick.xedgegestures.ui.theme.LocalNeon
import kotlin.math.roundToInt

internal fun triggerColor(t: EdgeTrigger) = Color(t.colorArgb.toInt())

/** Triggers of [edge] in on-screen order (top→bottom / left→right). */
internal fun orderedTriggers(s: AppSettings, edge: Edge) = s.edge(edge).triggers.sortedBy { it.startPct }

/**
 * Miniature of the target screen with every edge zone drawn in its trigger color. Zones on
 * [selectedEdge] are bright and tappable; the rest are dimmed context.
 */
@Composable
internal fun ScreenMap(
    s: AppSettings,
    selectedEdge: Edge,
    selectedId: String?,
    onSelect: (Edge, String) -> Unit,
) {
    val ctx = LocalContext.current
    val aspect = remember {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val d = dm.getDisplay(Config.TARGET_DISPLAY_ID) ?: dm.getDisplay(0)
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        (m.widthPixels.toFloat() / m.heightPixels).coerceIn(0.4f, 2.5f)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.heightIn(max = 190.dp).aspectRatio(aspect)
                .background(Color(0xFF050507), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2A2A30), RoundedCornerShape(8.dp)),
        ) {
            val w = maxWidth
            val h = maxHeight
            for (edge in Edge.entries) {
                val cfg = s.edge(edge)
                val onEdge = edge == selectedEdge
                val thick = if (onEdge) 14.dp else 8.dp
                orderedTriggers(s, edge).forEachIndexed { i, t ->
                    val vertical = edge == Edge.Right || edge == Edge.Left
                    val axis = if (vertical) h else w
                    val start = axis * (t.startPct / 100f)
                    val len = (axis * ((t.endPct - t.startPct) / 100f)).coerceAtLeast(2.dp)
                    val x = when (edge) { Edge.Right -> w - thick; Edge.Left -> 0.dp; else -> start }
                    val y = when (edge) { Edge.Bottom -> h - thick; Edge.Top -> 0.dp; else -> start }
                    val live = cfg.enabled && t.enabled
                    val selected = onEdge && t.id == selectedId
                    val alpha = when {
                        selected -> 1f
                        onEdge && live -> 0.6f
                        live -> 0.3f
                        else -> 0.12f
                    }
                    Box(
                        Modifier.offset(x, y)
                            .size(if (vertical) thick else len, if (vertical) len else thick)
                            .background(triggerColor(t).copy(alpha = alpha), RoundedCornerShape(3.dp))
                            .then(if (selected) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(3.dp)) else Modifier)
                            .clickable { onSelect(edge, t.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (onEdge) Text("${i + 1}", color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** One chip per trigger plus ADD / SPLIT shortcuts. */
@Composable
internal fun TriggerChips(
    triggers: List<EdgeTrigger>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onSplit: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        triggers.forEachIndexed { i, t ->
            val c = triggerColor(t)
            val sel = t.id == selectedId
            Row(
                Modifier.background(if (sel) c.copy(alpha = 0.22f) else ChipBg, RoundedCornerShape(8.dp))
                    .border(1.dp, c.copy(alpha = if (sel) 1f else 0.45f), RoundedCornerShape(8.dp))
                    .clickable { onSelect(t.id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(c.copy(alpha = if (t.enabled) 1f else 0.3f), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("${i + 1}", color = if (sel) c else TextHi,
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            }
        }
        SmallChip("+ ADD", onAdd)
        SmallChip("SPLIT ×2") { onSplit(2) }
        SmallChip("SPLIT ×3") { onSplit(3) }
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    val neon = LocalNeon.current
    Box(
        Modifier.background(ChipBg, RoundedCornerShape(8.dp))
            .border(1.dp, neon.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = neon.color, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ColorSwatches(selected: Long, onPick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TriggerPalette.forEach { argb ->
            val sel = argb == selected
            Box(
                Modifier.size(26.dp)
                    .background(Color(argb.toInt()), CircleShape)
                    .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                    .clickable { onPick(argb) },
            )
        }
    }
}

/** Start/end of the zone along the edge, in % of its length. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PositionRow(edge: Edge, t: EdgeTrigger, onChange: (Float, Float) -> Unit) {
    val c = triggerColor(t)
    val axis = if (edge == Edge.Right || edge == Edge.Left) "TOP → BOTTOM" else "LEFT → RIGHT"
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text("POSITION ($axis)", color = TextDim, style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text("${t.startPct.roundToInt()}% – ${t.endPct.roundToInt()}%", color = TextHi,
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        RangeSlider(
            value = t.startPct..t.endPct,
            onValueChange = { r ->
                onChange((r.start * 2).roundToInt() / 2f, (r.endInclusive * 2).roundToInt() / 2f)
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = c, activeTrackColor = c, inactiveTrackColor = Color(0xFF2A2A30),
            ),
        )
    }
}

@Composable
internal fun DangerButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(ChipBg, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFFF3B3B).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color(0xFFFF3B3B), fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun EmptyTriggers() {
    Text("No triggers on this edge. Tap + ADD.", color = TextMid,
        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 8.dp).height(20.dp))
}
