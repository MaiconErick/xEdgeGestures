package com.maiconerick.xedgegestures.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.actions.HomeResolver
import com.maiconerick.xedgegestures.overlay.Edge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "edge_gestures_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val BLOB = stringPreferencesKey("app_settings_json")

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[BLOB]
            val parsed = if (raw.isNullOrBlank()) AppSettings()
            else runCatching { json.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }
            migrate(parsed)
        }

    /**
     * One-way clean-up of settings that were saved before a behavior change.
     *
     * homeAppComponent: an earlier build's app picker listed CATEGORY_LAUNCHER
     * entries, so picking Nova stored its `NovaShortcutHandler` activity, which
     * shows a "please update" dialog. Swap it for the same package's HOME
     * activity so HOME still targets the launcher the user chose.
     */
    private fun migrate(s: AppSettings): AppSettings {
        var out = s
        if (out.homeAppComponent.contains("ShortcutHandler", ignoreCase = true)) {
            val pkg = out.homeAppComponent.substringBefore('/')
            val home = HomeResolver.homeComponentFor(context.packageManager, pkg)
            out = out.copy(homeAppComponent = home ?: "")
        }
        for (e in Edge.entries) {
            val cfg = out.edge(e)
            if (cfg.triggers.isEmpty()) out = out.withEdge(e, cfg.copy(triggers = listOf(legacyTrigger(e, cfg))))
        }
        return out
    }

    /** Converts a pre-trigger single-zone edge into one trigger with the same behavior. */
    private fun legacyTrigger(e: Edge, cfg: EdgeConfig): EdgeTrigger {
        var start = 0f
        var end = 100f
        if (cfg.lengthDp > 0) {
            // The old zone was centered on the edge; express its length as a % of the edge.
            val m = TargetDisplay.resolve(context)?.let { d ->
                android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
            }
            if (m != null) {
                val edgeDp = (if (e == Edge.Right || e == Edge.Left) m.heightPixels else m.widthPixels) / m.density
                val pct = (cfg.lengthDp / edgeDp * 100f).coerceIn(1f, 100f)
                start = 50f - pct / 2f
                end = 50f + pct / 2f
            }
        }
        val color = when (e) {
            Edge.Right -> TriggerPalette[0]
            Edge.Left -> TriggerPalette[1]
            Edge.Bottom -> TriggerPalette[2]
            Edge.Top -> TriggerPalette[3]
        }
        return EdgeTrigger(
            enabled = true, startPct = start, endPct = end, sizeDp = cfg.sizeDp, colorArgb = color,
            shortSwipe = cfg.shortSwipe, shortSwipeArg = cfg.shortSwipeArg,
            longSwipe = cfg.longSwipe, longSwipeArg = cfg.longSwipeArg, slider = cfg.slider,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[BLOB]?.let {
                runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull()
            } ?: AppSettings()
            prefs[BLOB] = json.encodeToString(transform(migrate(current)))
        }
    }
}
