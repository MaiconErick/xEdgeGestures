package com.maiconerick.xedgegestures.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maiconerick.xedgegestures.actions.HomeResolver
import com.maiconerick.xedgegestures.actions.SliderKind
import com.maiconerick.xedgegestures.data.prefs.ActionType
import com.maiconerick.xedgegestures.data.prefs.AppSettings
import com.maiconerick.xedgegestures.data.prefs.EdgeConfig
import com.maiconerick.xedgegestures.data.prefs.EdgeTrigger
import com.maiconerick.xedgegestures.data.prefs.EdgeTriggers
import com.maiconerick.xedgegestures.data.prefs.SettingsRepository
import com.maiconerick.xedgegestures.data.prefs.StatType
import com.maiconerick.xedgegestures.overlay.Edge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LaunchableApp(val label: String, val component: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
) : ViewModel() {

    val state = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setEnabled(v: Boolean) = update { it.copy(enabled = v) }
    fun setDebugTint(v: Boolean) = update { it.copy(debugTint = v) }
    fun setShowVolumeUi(v: Boolean) = update { it.copy(showVolumeSystemUi = v) }
    fun setAccent(name: String) = update { it.copy(accentName = name) }
    fun setHomeApp(component: String) = update { it.copy(homeAppComponent = component) }
    fun setInfoBarEnabled(v: Boolean) = update { it.copy(infoBarEnabled = v) }
    fun toggleStat(stat: StatType) = update {
        val s = it.infoBarStats.toMutableSet()
        if (stat in s) s.remove(stat) else s.add(stat)
        it.copy(infoBarStats = s)
    }
    fun setTargetMinBrightness(pct: Int) = update {
        it.copy(targetDisplayMinBrightnessPct = pct.coerceIn(0, 30))
    }
    fun setSliderEdgePadding(dp: Int) = update {
        it.copy(sliderEdgePaddingDp = dp.coerceIn(0, 200))
    }

    fun setEdge(edge: Edge, transform: (EdgeConfig) -> EdgeConfig) = update { s ->
        s.withEdge(edge, transform(s.edge(edge)))
    }

    fun setEdgeEnabled(edge: Edge, enabled: Boolean) = setEdge(edge) { it.copy(enabled = enabled) }

    fun setTrigger(edge: Edge, id: String, transform: (EdgeTrigger) -> EdgeTrigger) = setEdge(edge) { cfg ->
        cfg.copy(triggers = cfg.triggers.map { if (it.id == id) transform(it) else it })
    }

    fun setTriggerRange(edge: Edge, id: String, start: Float, end: Float) = setTrigger(edge, id) {
        val a = start.coerceIn(0f, 100f)
        val b = end.coerceIn(0f, 100f)
        it.copy(startPct = minOf(a, b), endPct = maxOf(a, b).coerceAtLeast(minOf(a, b) + 1f).coerceAtMost(100f))
    }
    fun setTriggerSize(edge: Edge, id: String, dp: Int) = setTrigger(edge, id) { it.copy(sizeDp = dp.coerceIn(4, 200)) }
    fun setTriggerColor(edge: Edge, id: String, argb: Long) = setTrigger(edge, id) { it.copy(colorArgb = argb) }
    fun setTriggerEnabled(edge: Edge, id: String, v: Boolean) = setTrigger(edge, id) { it.copy(enabled = v) }
    fun setShortSwipe(edge: Edge, id: String, type: ActionType, arg: String = "") =
        setTrigger(edge, id) { it.copy(shortSwipe = type, shortSwipeArg = arg) }
    fun setLongSwipe(edge: Edge, id: String, type: ActionType, arg: String = "") =
        setTrigger(edge, id) { it.copy(longSwipe = type, longSwipeArg = arg) }
    fun setSlider(edge: Edge, id: String, slider: SliderKind) = setTrigger(edge, id) { it.copy(slider = slider) }

    /** Adds a trigger in the widest free gap of the edge (or over the whole edge if none). */
    fun addTrigger(edge: Edge, onAdded: (String) -> Unit) {
        val t = EdgeTriggers.newTrigger(state.value.edge(edge).triggers)
        setEdge(edge) { it.copy(triggers = it.triggers + t) }
        onAdded(t.id)
    }

    fun removeTrigger(edge: Edge, id: String) = setEdge(edge) { cfg ->
        cfg.copy(triggers = cfg.triggers.filterNot { it.id == id })
    }

    /** Replaces the edge's zones with [n] equal ones, keeping existing actions/colors in order. */
    fun splitEdge(edge: Edge, n: Int) = setEdge(edge) { cfg ->
        cfg.copy(triggers = EdgeTriggers.split(cfg.triggers.sortedBy { it.startPct }, n))
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun launchableApps(ctx: Context): List<LaunchableApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    component = "${it.activityInfo.packageName}/${it.activityInfo.name}",
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Launchers only, keyed by their HOME activity (not the app-drawer entry). */
    fun homeApps(ctx: Context): List<LaunchableApp> {
        val pm = ctx.packageManager
        return HomeResolver.homeActivities(pm)
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    component = "${it.activityInfo.packageName}/${it.activityInfo.name}",
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }
}
