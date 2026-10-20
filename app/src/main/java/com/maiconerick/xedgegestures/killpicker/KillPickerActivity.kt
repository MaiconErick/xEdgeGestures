package com.maiconerick.xedgegestures.killpicker

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.ui.theme.XEdgeGesturesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DisplayApp(
    val displayId: Int,
    val displayLabel: String,
    val component: ComponentName?,
    val label: String,
    val icon: Drawable?,
)

@AndroidEntryPoint
class KillPickerActivity : ComponentActivity() {

    @Inject lateinit var resolver: TopAppResolver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XEdgeGesturesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xCC000000),
                ) {
                    KillPickerUI(
                        resolver = resolver,
                        onKilled = { finish() },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun KillPickerUI(
    resolver: TopAppResolver,
    onKilled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<DisplayApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        TargetDisplay.resolve(ctx)
        val displays = if (TargetDisplay.isSecondary) listOf(0, TargetDisplay.id) else listOf(0)
        val labels = mapOf(0 to "Main (0)", TargetDisplay.id to "Second (${TargetDisplay.id})")
        items = withContext(Dispatchers.IO) {
            val top = resolver.topPerDisplay(displays, ctx.packageName)
            displays.map { d ->
                val comp = top[d]
                val pm = ctx.packageManager
                val (label, icon) = if (comp != null) {
                    val info = runCatching { pm.getActivityInfo(comp, 0) }.getOrNull()
                    val lbl = info?.loadLabel(pm)?.toString() ?: comp.packageName
                    val ic = info?.loadIcon(pm) ?: pm.getApplicationIcon(comp.packageName)
                    lbl to ic
                } else {
                    "(unknown)" to null
                }
                DisplayApp(
                    displayId = d,
                    displayLabel = labels[d] ?: "Display $d",
                    component = comp,
                    label = label,
                    icon = icon,
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Force-stop", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap the app you want to kill",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { app ->
                    AppCard(
                        app = app,
                        onKill = {
                            app.component?.let { c ->
                                resolver.forceStop(c.packageName)
                            }
                            onKilled()
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
private fun AppCard(app: DisplayApp, onKill: () -> Unit) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(enabled = app.component != null, onClick = onKill),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                app.displayLabel,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(12.dp))
            val icon = app.icon
            if (icon != null) {
                val bmp = remember(icon) { icon.toBitmap(96, 96) }
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(64.dp))
            } else {
                Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)))
            }
            Spacer(Modifier.height(10.dp))
            Text(app.label, style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                app.component?.packageName ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
