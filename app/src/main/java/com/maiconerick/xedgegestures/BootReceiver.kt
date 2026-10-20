package com.maiconerick.xedgegestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.maiconerick.xedgegestures.service.EdgeGesturesService

/** Auto-starts the FG service on boot, but only if the overlay permission is granted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        if (!Settings.canDrawOverlays(context)) return
        // The system binds the accessibility service by itself after boot.
        if (com.maiconerick.xedgegestures.service.EdgeAccessibilityService.isEnabled(context)) return
        EdgeGesturesService.start(context)
    }
}
