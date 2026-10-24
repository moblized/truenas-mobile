package app.truenasmobile

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * Swaps the home-screen icon between the light and dark versions to match the app's theme.
 * Called when the app goes to the background, because switching the launcher entry while
 * the app is on screen can close it on some phones.
 */
object AppIcon {
    fun sync(activity: Activity, dark: Boolean) {
        val base = activity.javaClass.name.substringBeforeLast('.')
        val pm = activity.packageManager
        val light = ComponentName(activity.packageName, "$base.LauncherLight")
        val darkAlias = ComponentName(activity.packageName, "$base.LauncherDark")
        val wanted = if (dark) darkAlias else light
        val other = if (dark) light else darkAlias

        // Leave it alone when it already matches, so the launcher isn't disturbed.
        if (isEnabled(pm, wanted, light) && !isEnabled(pm, other, light)) return

        runCatching {
            // Turn the new one on first so there's always an icon to tap.
            pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private fun isEnabled(pm: PackageManager, component: ComponentName, light: ComponentName): Boolean =
        when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> component == light
            else -> false
        }
}
