package com.maiconerick.xedgegestures.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.maiconerick.xedgegestures.root.RootShell
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Preferred host for the overlays. system_server binds accessibility services at
 * near-foreground priority and rebinds them if the process dies, so heavy games
 * (Minecraft) no longer get the gestures killed by the low-memory killer the way the
 * plain foreground service did. It listens to no events; it only exists to stay alive.
 */
@AndroidEntryPoint
class EdgeAccessibilityService : AccessibilityService() {

    @Inject lateinit var host: OverlayHost

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "connected; taking over from the FG service")
        EdgeGesturesService.stop(this)
        host.start(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        host.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EdgeA11yService"

        @Volatile var instance: EdgeAccessibilityService? = null
            private set

        fun component(ctx: Context) = ComponentName(ctx, EdgeAccessibilityService::class.java)

        /** True when the service is listed in the system's enabled accessibility services. */
        fun isEnabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = component(ctx)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Adds the service to the enabled list through root. Blocking; call off the main thread. */
        fun enableViaRoot(ctx: Context): Boolean {
            if (isEnabled(ctx)) return true
            val me = component(ctx).flattenToString()
            val cur = RootShell.capture("settings get secure enabled_accessibility_services")
                ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            val next = if (cur == null) me else "$cur:$me"
            return RootShell.exec("settings put secure enabled_accessibility_services '$next'") &&
                RootShell.exec("settings put secure accessibility_enabled 1")
        }

        /** Stops the overlays and removes the service from the enabled list. */
        fun disable() {
            instance?.disableSelf()
        }
    }
}
