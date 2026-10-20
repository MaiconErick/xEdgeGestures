package com.maiconerick.xedgegestures.actions

import android.content.Context
import android.util.Log
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.data.prefs.ActionType
import com.maiconerick.xedgegestures.data.prefs.AppSettings
import com.maiconerick.xedgegestures.infobar.InfoBarController
import com.maiconerick.xedgegestures.killpicker.KillPickerActivity
import com.maiconerick.xedgegestures.root.RootShell
import com.maiconerick.xedgegestures.screenshot.Screenshotter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget action dispatcher. Every action posts to an IO scope; the
 * caller (touch listener on main thread) returns immediately. Blocking the
 * touch thread on `su -c cmd`.waitFor() was ANR-ing the whole app.
 */
@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val screenshotter: Screenshotter,
    private val infoBar: InfoBarController,
) {

    @Volatile var homeComponent: String = AppSettings.DEFAULT_HOME
    @Volatile var showVolumeSystemUi: Boolean = true

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun execute(action: Action) {
        Log.d(TAG, "execute $action")
        when (action) {
            Action.Back -> shell("input -d ${TargetDisplay.id} keyevent KEYCODE_BACK")
            Action.Home -> shell(
                if (homeComponent.isBlank()) {
                    // Generic HOME intent: resolves to the system default home. On the
                    // Thor that's Mjolnir, which opens home on both displays.
                    "am start -a android.intent.action.MAIN -c android.intent.category.HOME --display ${TargetDisplay.id}"
                } else {
                    // Explicit component with no HOME category: adding the category
                    // makes the system also bring Display 0's home task forward.
                    "am start --display ${TargetDisplay.id} -n $homeComponent"
                }
            )
            Action.Recents -> shell("input -d ${TargetDisplay.id} keyevent KEYCODE_APP_SWITCH")
            is Action.LaunchApp -> shell(
                "am start --display ${TargetDisplay.id} -n ${action.component}"
            )
            Action.KillPicker -> shell(
                "am start --display ${TargetDisplay.id} " +
                    "-n ${ctx.packageName}/${KillPickerActivity::class.java.name}"
            )
            Action.Screenshot -> screenshotter.capture()  // already goes to its own IO scope
            Action.ToggleInfoBar -> infoBar.toggleManual() // main-thread-safe
            Action.None -> {}
        }
    }

    fun fromConfig(type: ActionType, arg: String): Action = when (type) {
        ActionType.None -> Action.None
        ActionType.Back -> Action.Back
        ActionType.Home -> Action.Home
        ActionType.Recents -> Action.Recents
        ActionType.LaunchApp -> if (arg.isBlank()) Action.None else Action.LaunchApp(arg)
        ActionType.KillPicker -> Action.KillPicker
        ActionType.Screenshot -> Action.Screenshot
        ActionType.ToggleInfoBar -> Action.ToggleInfoBar
    }

    private fun shell(cmd: String) {
        scope.launch { RootShell.exec(cmd) }
    }

    companion object { private const val TAG = "xEdgeActionExecutor" }
}
