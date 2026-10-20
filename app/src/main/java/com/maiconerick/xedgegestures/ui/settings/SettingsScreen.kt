package com.maiconerick.xedgegestures.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maiconerick.xedgegestures.actions.SliderKind
import com.maiconerick.xedgegestures.data.prefs.ActionType
import com.maiconerick.xedgegestures.data.prefs.AppSettings
import com.maiconerick.xedgegestures.data.prefs.EdgeTrigger
import com.maiconerick.xedgegestures.data.prefs.StatType
import com.maiconerick.xedgegestures.overlay.Edge
import com.maiconerick.xedgegestures.ui.theme.LocalNeon
import com.maiconerick.xedgegestures.ui.theme.NeonAccent

private enum class AppPickerTarget { Home, ShortSwipe, LongSwipe }

/** Points at one trigger on one edge (dialogs edit it). */
private data class TriggerRef(val edge: Edge, val id: String)

internal val TextHi = Color(0xFFEDEDED)
internal val TextMid = Color(0xFFB0B0BC)
internal val TextDim = Color(0xFF7A7A88)
internal val CardBg = Color(0xFF0B0B0D)
internal val ChipBg = Color(0xFF14141C)

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    selectedEdge: Edge,
    modifier: Modifier = Modifier,
    onEdgeSelect: (Edge) -> Unit = {},
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var pickingApp by remember { mutableStateOf<AppPickerTarget?>(null) }
    var appPickerEdge by remember { mutableStateOf<TriggerRef?>(null) }
    var shortSwipeFor by remember { mutableStateOf<TriggerRef?>(null) }
    var longSwipeFor by remember { mutableStateOf<TriggerRef?>(null) }
    var sliderFor by remember { mutableStateOf<TriggerRef?>(null) }
    // Selected trigger per edge; falls back to the edge's first trigger.
    var pickedTrigger by remember { mutableStateOf<Map<Edge, String>>(emptyMap()) }
    val edgeTriggers = orderedTriggers(s, selectedEdge)
    val selTrigger = edgeTriggers.firstOrNull { it.id == pickedTrigger[selectedEdge] } ?: edgeTriggers.firstOrNull()

    val edgeCard: @Composable () -> Unit = {
        EdgeCard(
            s = s, edge = selectedEdge, trigger = selTrigger,
            onToggle = { vm.setEdgeEnabled(selectedEdge, it) },
            onPreview = vm::setDebugTint,
            onSelect = { e, id -> pickedTrigger = pickedTrigger + (e to id); if (e != selectedEdge) onEdgeSelect(e) },
            onAdd = { vm.addTrigger(selectedEdge) { id -> pickedTrigger = pickedTrigger + (selectedEdge to id) } },
            onSplit = { vm.splitEdge(selectedEdge, it) },
            vm = vm,
            onShort = { id -> shortSwipeFor = TriggerRef(selectedEdge, id) },
            onLong = { id -> longSwipeFor = TriggerRef(selectedEdge, id) },
            onSlider = { id -> sliderFor = TriggerRef(selectedEdge, id) },
        )
    }
    var accentPicker by remember { mutableStateOf(false) }

    if (landscape) {
        Row(
            modifier = modifier.fillMaxSize().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.weight(0.4f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MasterCard(s, vm, onAccentPick = { accentPicker = true })
                HomeAppCard(s, vm,
                    onPick = { appPickerEdge = null; pickingApp = AppPickerTarget.Home })
                InfoBarCard(s, vm)
            }
            Column(
                Modifier.weight(0.6f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                edgeCard()
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MasterCard(s, vm, onAccentPick = { accentPicker = true }) }
            item {
                HomeAppCard(s, vm,
                    onPick = { appPickerEdge = null; pickingApp = AppPickerTarget.Home })
            }
            item {
                edgeCard()
            }
            item { InfoBarCard(s, vm) }
        }
    }

    // ---- Dialogs ----

    if (pickingApp != null) {
        AppPickerDialog(
            apps = remember(pickingApp) {
                if (pickingApp == AppPickerTarget.Home) vm.homeApps(ctx) else vm.launchableApps(ctx)
            },
            onPick = { app ->
                when (pickingApp) {
                    AppPickerTarget.Home -> vm.setHomeApp(app.component)
                    AppPickerTarget.ShortSwipe -> appPickerEdge?.let {
                        vm.setShortSwipe(it.edge, it.id, ActionType.LaunchApp, app.component)
                    }
                    AppPickerTarget.LongSwipe -> appPickerEdge?.let {
                        vm.setLongSwipe(it.edge, it.id, ActionType.LaunchApp, app.component)
                    }
                    null -> {}
                }
                pickingApp = null; appPickerEdge = null
            },
            onDismiss = { pickingApp = null; appPickerEdge = null },
        )
    }
    fun triggerOf(r: TriggerRef) = s.edge(r.edge).triggers.firstOrNull { it.id == r.id }
    shortSwipeFor?.let { r ->
        ActionPickerDialog(
            title = "SHORT SWIPE — ${r.edge.name}",
            selected = triggerOf(r)?.shortSwipe ?: ActionType.None,
            onPick = {
                if (it == ActionType.LaunchApp) {
                    appPickerEdge = r; pickingApp = AppPickerTarget.ShortSwipe
                } else vm.setShortSwipe(r.edge, r.id, it)
                shortSwipeFor = null
            },
            onDismiss = { shortSwipeFor = null },
        )
    }
    longSwipeFor?.let { r ->
        ActionPickerDialog(
            title = "SWIPE & HOLD — ${r.edge.name}",
            selected = triggerOf(r)?.longSwipe ?: ActionType.None,
            onPick = {
                if (it == ActionType.LaunchApp) {
                    appPickerEdge = r; pickingApp = AppPickerTarget.LongSwipe
                } else vm.setLongSwipe(r.edge, r.id, it)
                longSwipeFor = null
            },
            onDismiss = { longSwipeFor = null },
        )
    }
    sliderFor?.let { r ->
        SliderPickerDialog(
            selected = triggerOf(r)?.slider ?: SliderKind.None,
            onPick = { vm.setSlider(r.edge, r.id, it); sliderFor = null },
            onDismiss = { sliderFor = null },
        )
    }
    if (accentPicker) {
        AccentPickerDialog(
            selected = NeonAccent.from(s.accentName),
            onPick = { vm.setAccent(it.name); accentPicker = false },
            onDismiss = { accentPicker = false },
        )
    }
}

// -------- cards ---------

@Composable
private fun MasterCard(s: AppSettings, vm: SettingsViewModel, onAccentPick: () -> Unit) {
    NeonCard {
        SwitchRow("Overlays enabled", "Master kill switch",
            s.enabled, vm::setEnabled)
        Divider()
        SwitchRow("Preview zones", "Tint every trigger on screen with its own color",
            s.debugTint, vm::setDebugTint)
        Divider()
        SwitchRow("Show OS volume UI", "Turn off for silent volume changes",
            s.showVolumeSystemUi, vm::setShowVolumeUi)
        Divider()
        SliderRow(
            label = "2nd screen min brightness",
            value = s.targetDisplayMinBrightnessPct,
            range = 0..30, unit = "%",
            onChange = vm::setTargetMinBrightness,
        )
        SliderRow(
            label = "Slider edge padding",
            value = s.sliderEdgePaddingDp,
            range = 0..200, unit = "dp",
            onChange = vm::setSliderEdgePadding,
        )
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Neon accent")
                Spacer(Modifier.height(2.dp))
                Text(NeonAccent.from(s.accentName).label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHi, fontFamily = FontFamily.Monospace)
            }
            TinyChangeButton(onAccentPick)
        }
    }
}

@Composable
private fun HomeAppCard(s: AppSettings, vm: SettingsViewModel, onPick: () -> Unit) {
    NeonCard {
        SectionTitle("Home app (HOME action)")
        Spacer(Modifier.height(4.dp))
        val isDefault = s.homeAppComponent.isBlank()
        Text(
            if (isDefault) "System default HOME (may open on both screens)" else s.homeAppComponent,
            color = if (isDefault) TextMid else TextHi,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyChangeButton(onPick, label = "PICK APP")
            if (!isDefault) TinyChangeButton({ vm.setHomeApp("") },
                label = "USE SYSTEM LAUNCHER")
        }
    }
}

@Composable
private fun InfoBarCard(s: AppSettings, vm: SettingsViewModel) {
    NeonCard {
        SwitchRow("Info bar",
            "Assign an edge action to ToggleInfoBar to show/hide manually",
            s.infoBarEnabled, vm::setInfoBarEnabled)
        Divider()
        SectionTitle("Stats")
        StatType.values().forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)) {
                Checkbox(
                    checked = st in s.infoBarStats,
                    onCheckedChange = { vm.toggleStat(st) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = LocalNeon.current.color,
                        uncheckedColor = TextDim,
                        checkmarkColor = Color.Black,
                    ),
                )
                Text(st.name.uppercase(), color = TextHi,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// -------- primitives ---------

@Composable
fun NeonCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val neon = LocalNeon.current
    Box(
        modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, neon.color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) { Column { content() } }
}

@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp)
            .background(Brush.horizontalGradient(listOf(
                Color.Transparent, Color(0xFF2A2A30), Color.Transparent,
            )))
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        color = LocalNeon.current.color,
        letterSpacing = 0.8.sp)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace)
            Text(subtitle, color = TextMid,
                style = MaterialTheme.typography.bodySmall)
        }
        NeonSwitch(checked, onChange)
    }
}

@Composable
private fun NeonSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.Black,
            checkedTrackColor = LocalNeon.current.color,
            checkedBorderColor = LocalNeon.current.color,
            uncheckedThumbColor = TextMid,
            uncheckedTrackColor = ChipBg,
            uncheckedBorderColor = TextDim,
        ),
    )
}

@Composable
private fun TinyChangeButton(onClick: () -> Unit, label: String = "CHANGE") {
    val neon = LocalNeon.current
    Box(
        modifier = Modifier
            .background(ChipBg, RoundedCornerShape(8.dp))
            .border(1.dp, neon.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = neon.color)
    }
}

@Composable
private fun EdgeCard(
    s: AppSettings,
    edge: Edge,
    trigger: EdgeTrigger?,
    onToggle: (Boolean) -> Unit,
    onPreview: (Boolean) -> Unit,
    onSelect: (Edge, String) -> Unit,
    onAdd: () -> Unit,
    onSplit: (Int) -> Unit,
    vm: SettingsViewModel,
    onShort: (String) -> Unit,
    onLong: (String) -> Unit,
    onSlider: (String) -> Unit,
) {
    val cfg = s.edge(edge)
    val triggers = orderedTriggers(s, edge)
    NeonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("${edge.name} edge")
            Spacer(Modifier.weight(1f))
            Text("PREVIEW", color = TextDim, style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(4.dp))
            NeonSwitch(s.debugTint, onPreview)
            Spacer(Modifier.width(10.dp))
            Text("ON", color = TextDim, style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(4.dp))
            NeonSwitch(cfg.enabled, onToggle)
        }
        Spacer(Modifier.height(6.dp))
        ScreenMap(s, edge, trigger?.id, onSelect)
        Spacer(Modifier.height(8.dp))
        TriggerChips(triggers, trigger?.id, { onSelect(edge, it) }, onAdd, onSplit)
        Divider()

        if (trigger == null) {
            EmptyTriggers()
            return@NeonCard
        }
        val idx = triggers.indexOfFirst { it.id == trigger.id } + 1
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(triggerColor(trigger), RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(8.dp))
            Text("TRIGGER $idx", color = triggerColor(trigger), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            NeonSwitch(trigger.enabled) { vm.setTriggerEnabled(edge, trigger.id, it) }
        }
        ColorSwatches(trigger.colorArgb) { vm.setTriggerColor(edge, trigger.id, it) }
        PositionRow(edge, trigger) { a, b -> vm.setTriggerRange(edge, trigger.id, a, b) }
        SliderRow(
            label = if (edge == Edge.Right || edge == Edge.Left) "Width" else "Height",
            value = trigger.sizeDp, range = 4..120, unit = "dp",
            onChange = { vm.setTriggerSize(edge, trigger.id, it) },
        )
        Divider()

        ActionRow("Short swipe", actionLabel(trigger.shortSwipe, trigger.shortSwipeArg)) { onShort(trigger.id) }
        ActionRow("Swipe & hold", actionLabel(trigger.longSwipe, trigger.longSwipeArg)) { onLong(trigger.id) }
        ActionRow("Hold-drag slider", trigger.slider.name) { onSlider(trigger.id) }
        Spacer(Modifier.height(6.dp))
        DangerButton("DELETE TRIGGER $idx") { vm.removeTrigger(edge, trigger.id) }
    }
}

private fun actionLabel(type: ActionType, arg: String) = when (type) {
    ActionType.LaunchApp -> "Launch: ${arg.substringBefore('/').ifBlank { "(unset)" }}"
    else -> type.name
}

@Composable
private fun ActionRow(label: String, value: String, onPick: () -> Unit) {
    val neon = LocalNeon.current
    val isSet = value != "None" && value != "Launch: (unset)"
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = TextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(2.dp))
            Text(value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (isSet) neon.color else TextDim)
        }
        TinyChangeButton(onPick)
    }
}

@Composable
internal fun SliderRow(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    val neon = LocalNeon.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = TextDim,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace)
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = neon.color,
                    activeTrackColor = neon.color,
                    inactiveTrackColor = Color(0xFF2A2A30),
                    activeTickColor = neon.color,
                    inactiveTickColor = Color(0xFF2A2A30),
                ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$value$unit", color = TextHi,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp))
    }
}

// -------- dialogs ---------

@Composable
private fun ActionPickerDialog(
    title: String, selected: ActionType,
    onPick: (ActionType) -> Unit, onDismiss: () -> Unit,
) {
    NeonDialog(onDismiss, title) {
        LazyColumn(modifier = Modifier.height(400.dp)) {
            items(ActionType.values().toList()) { t ->
                PickerRow(t.name, t == selected) { onPick(t) }
            }
        }
    }
}

@Composable
private fun SliderPickerDialog(
    selected: SliderKind, onPick: (SliderKind) -> Unit, onDismiss: () -> Unit,
) {
    NeonDialog(onDismiss, "HOLD-DRAG SLIDER") {
        LazyColumn(modifier = Modifier.height(280.dp)) {
            items(SliderKind.values().toList()) { k ->
                PickerRow(k.name, k == selected) { onPick(k) }
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<LaunchableApp>,
    onPick: (LaunchableApp) -> Unit, onDismiss: () -> Unit,
) {
    NeonDialog(onDismiss, "PICK APP") {
        LazyColumn(modifier = Modifier.height(460.dp)) {
            items(apps) { a ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(onClick = { onPick(a) })
                        .padding(vertical = 8.dp),
                ) {
                    Text(a.label, color = TextHi, fontFamily = FontFamily.Monospace)
                    Text(a.component, color = TextMid,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AccentPickerDialog(
    selected: NeonAccent, onPick: (NeonAccent) -> Unit, onDismiss: () -> Unit,
) {
    NeonDialog(onDismiss, "NEON ACCENT") {
        LazyColumn(modifier = Modifier.height(360.dp)) {
            items(NeonAccent.values().toList()) { a ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(onClick = { onPick(a) })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(18.dp).background(a.color, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, fontFamily = FontFamily.Monospace,
                        color = if (a == selected) a.color else TextHi)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val neon = LocalNeon.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓ " else "  ",
            color = neon.color, fontFamily = FontFamily.Monospace)
        Text(label, fontFamily = FontFamily.Monospace,
            color = if (selected) neon.color else TextHi)
    }
}

@Composable
private fun NeonDialog(
    onDismissRequest: () -> Unit, title: String,
    content: @Composable () -> Unit,
) {
    val neon = LocalNeon.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("CLOSE", color = neon.color, fontFamily = FontFamily.Monospace)
            }
        },
        title = { Text(title, color = TextHi, fontFamily = FontFamily.Monospace) },
        text = { content() },
        containerColor = Color(0xFF0B0B0D),
        titleContentColor = TextHi,
        textContentColor = TextHi,
    )
}
