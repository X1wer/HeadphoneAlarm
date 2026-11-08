package hu.ulpifex.fulesebreszto.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.audio.VibrationController

data class PermissionStatus(
    val exactAlarms: Boolean = true,
    val notifications: Boolean = true,
    val fullScreen: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val hasVibrator: Boolean = true,
) {
    /** Missing items (string resources) that can prevent an alarm from ringing or showing. */
    val criticalMissing: List<Int>
        get() = buildList {
            if (!exactAlarms) add(R.string.perm_exact)
            if (!notifications) add(R.string.perm_notifications)
            if (!fullScreen) add(R.string.perm_full_screen)
        }
}

object Permissions {
    fun read(context: Context, container: AppContainer): PermissionStatus = PermissionStatus(
        exactAlarms = container.alarmClock.canScheduleExact(),
        notifications = container.notifications.canPost(),
        fullScreen = container.notifications.canUseFullScreen(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        hasVibrator = VibrationController(context).hasVibrator,
    )

    private fun pkg(context: Context) = Uri.parse("package:${context.packageName}")

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(context))
        } else {
            appDetails(context)
        }

    fun notificationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(context))
        } else {
            notificationsIntent(context)
        }

    /** Opens the battery optimisation list (no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission needed). */
    fun batteryIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetails(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(context))

    fun open(context: Context, intent: Intent) {
        val launch = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
            .recoverCatching { context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
