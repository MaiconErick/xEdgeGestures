package com.maiconerick.xedgegestures.service

import android.content.Context
import com.maiconerick.xedgegestures.data.prefs.SettingsRepository
import com.maiconerick.xedgegestures.infobar.InfoBarController
import com.maiconerick.xedgegestures.overlay.EdgeOverlayEngine
import com.maiconerick.xedgegestures.slider.SliderIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mounts the edge overlays + info bar and keeps them in sync with settings. Shared by the
 * accessibility service (preferred: survives low-memory kills) and the plain FG service
 * (fallback when accessibility can't be enabled).
 */
@Singleton
class OverlayHost @Inject constructor(
    private val engine: EdgeOverlayEngine,
    private val settings: SettingsRepository,
    private val infoBar: InfoBarController,
    private val indicator: SliderIndicator,
) {
    private var scope: CoroutineScope? = null

    fun start(ctx: Context) {
        stop()
        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s
        s.launch {
            settings.settings.collectLatest { st ->
                val accentArgb = com.maiconerick.xedgegestures.ui.theme.NeonAccent
                    .from(st.accentName).color.let { c ->
                        android.graphics.Color.argb(
                            (c.alpha * 255).toInt(),
                            (c.red * 255).toInt(),
                            (c.green * 255).toInt(),
                            (c.blue * 255).toInt(),
                        )
                    }
                indicator.accentArgb = accentArgb
                engine.apply(ctx.applicationContext, st)
                infoBar.applySettings(st.enabled && st.infoBarEnabled, st.infoBarStats, accentArgb)
            }
        }
    }

    fun stop() {
        val s = scope ?: return
        scope = null
        s.cancel()
        engine.tearDown()
        infoBar.applySettings(false, emptySet(), null)
    }
}
