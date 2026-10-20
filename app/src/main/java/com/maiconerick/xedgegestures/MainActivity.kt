package com.maiconerick.xedgegestures

import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maiconerick.xedgegestures.overlay.Edge
import com.maiconerick.xedgegestures.service.EdgeAccessibilityService
import com.maiconerick.xedgegestures.service.EdgeGesturesService
import com.maiconerick.xedgegestures.ui.settings.SettingsScreen
import com.maiconerick.xedgegestures.ui.settings.SettingsViewModel
import com.maiconerick.xedgegestures.ui.theme.LocalNeon
import com.maiconerick.xedgegestures.ui.theme.NeonAccent
import com.maiconerick.xedgegestures.ui.theme.StarfieldBackground
import com.maiconerick.xedgegestures.ui.theme.XEdgeGesturesTheme
import dagger.hilt.android.AndroidEntryPoint

private val TextHi = Color(0xFFEDEDED)
private val TextMid = Color(0xFFB0B0BC)
private val ChipBg = Color(0xFF14141C)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SettingsViewModel = hiltViewModel()
            val s by vm.state.collectAsStateWithLifecycle()
            val accent = NeonAccent.from(s.accentName)
            XEdgeGesturesTheme(accent = accent) {
                Root()
            }
        }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var writeGranted by remember { mutableStateOf(Settings.System.canWrite(ctx)) }
    var displayIds by remember { mutableStateOf(emptyList<Int>()) }
    var selectedEdge by remember { mutableStateOf(Edge.Right) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(ctx)
                writeGranted = Settings.System.canWrite(ctx)
                displayIds = ctx.getSystemService(DisplayManager::class.java)
                    .displays.map { it.displayId }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val hasTarget = Config.TARGET_DISPLAY_ID in displayIds
    val targetId = if (hasTarget) Config.TARGET_DISPLAY_ID else 0

    Box(Modifier.fillMaxSize()) {
        StarfieldBackground(Modifier.fillMaxSize(), enabled = true)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopBar(
                    displayIds = displayIds, targetId = targetId,
                    overlayGranted = overlayGranted, writeGranted = writeGranted,
                    selectedEdge = selectedEdge,
                    onEdgeSelect = { selectedEdge = it },
                    onOverlay = {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onWrite = {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onStart = {
                        // Prefer the accessibility host (survives low memory); fall back to
                        // the FG service when root can't enable it.
                        Thread {
                            if (!EdgeAccessibilityService.enableViaRoot(ctx)) {
                                EdgeGesturesService.start(ctx)
                            }
                        }.start()
                    },
                    onStop = {
                        EdgeAccessibilityService.disable()
                        EdgeGesturesService.stop(ctx)
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            ) {
                SettingsScreen(
                    selectedEdge = selectedEdge,
                    onEdgeSelect = { selectedEdge = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    displayIds: List<Int>,
    targetId: Int,
    overlayGranted: Boolean,
    writeGranted: Boolean,
    selectedEdge: Edge,
    onEdgeSelect: (Edge) -> Unit,
    onOverlay: () -> Unit,
    onWrite: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val neon = LocalNeon.current

    Column(Modifier.fillMaxWidth().statusBarsPadding()
        .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(neon.color, RoundedCornerShape(3.dp)))
            Spacer(Modifier.size(9.dp))
            Text("xEdgeGestures",
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                fontFamily = FontFamily.Monospace, color = TextHi)
            Spacer(Modifier.weight(1f))
            Text(
                "d:${displayIds.joinToString(",")} • tgt:$targetId • " +
                "ovr:${chip(overlayGranted)} • wr:${chip(writeGranted)}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Edge.values().forEach { e ->
                EdgeTab(e, selectedEdge == e) { onEdgeSelect(e) }
            }
            Spacer(Modifier.weight(1f))
            if (!overlayGranted) StatusButton("OVERLAY", onOverlay)
            if (!writeGranted) StatusButton("WRITE", onWrite)
            StatusButton("START", onStart, primary = true)
            StatusButton("STOP", onStop)
        }
    }
}

private fun chip(b: Boolean) = if (b) "OK" else "NO"

@Composable
private fun EdgeTab(edge: Edge, selected: Boolean, onClick: () -> Unit) {
    val neon = LocalNeon.current
    val bg = if (selected) neon.color.copy(alpha = 0.18f) else ChipBg
    val borderColor = if (selected) neon.color else neon.color.copy(alpha = 0.35f)
    Box(
        Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(edge.name.uppercase(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) neon.color else TextMid,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    val neon = LocalNeon.current
    val bg = if (primary) neon.color.copy(alpha = 0.18f) else ChipBg
    val border = if (primary) neon.color else neon.color.copy(alpha = 0.4f)
    Box(
        Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = neon.color, fontWeight = FontWeight.Bold)
    }
}
