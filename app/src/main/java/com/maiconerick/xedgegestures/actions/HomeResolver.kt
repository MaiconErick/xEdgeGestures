package com.maiconerick.xedgegestures.actions

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Finds launchers' real HOME activities (ACTION_MAIN + CATEGORY_HOME).
 *
 * A launcher's app-drawer (CATEGORY_LAUNCHER) entry is often not its home
 * screen: Nova's is `NovaShortcutHandler`, which shows a "please update" dialog.
 * The HOME action has to target the HOME activity explicitly. Firing the generic
 * HOME intent resolves to the system default (Mjolnir on the Thor), which opens
 * home on both displays.
 */
object HomeResolver {

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** All HOME activities, minus Settings' FallbackHome placeholder. */
    fun homeActivities(pm: PackageManager): List<ResolveInfo> =
        pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_ALL)
            .filterNot { it.activityInfo.name.endsWith(".FallbackHome") }

    /** "pkg/cls" of [packageName]'s HOME activity, or null if it isn't a launcher. */
    fun homeComponentFor(pm: PackageManager, packageName: String): String? =
        pm.queryIntentActivities(homeIntent().setPackage(packageName), PackageManager.MATCH_ALL)
            .firstOrNull()
            ?.activityInfo
            ?.let { "${it.packageName}/${it.name}" }
}
