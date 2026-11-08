package hu.ulpifex.fulesebreszto.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import hu.ulpifex.fulesebreszto.FulesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Receives AlarmManager triggers and hands them to the ringing service immediately. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as FulesApp
        when (intent.action) {
            ACTION_FIRE -> {
                StartupWakeLock.acquire(context)
                val service = Intent(context, AlarmRingService::class.java)
                    .setAction(AlarmRingService.ACTION_TRIGGER)
                    .putExtras(intent)
                try {
                    // Starting a foreground service from a setAlarmClock() broadcast is an
                    // explicitly allowed background-start exemption.
                    ContextCompat.startForegroundService(context, service)
                } catch (e: Exception) {
                    app.container.log.log("ring service start failed: ${e.javaClass.simpleName}")
                    StartupWakeLock.release()
                    app.container.notifications.failedToStart("Ébresztés")
                }
            }

            ACTION_CANCEL_SNOOZE -> {
                val id = intent.getLongExtra(AndroidAlarmClock.EXTRA_ALARM_ID, Long.MIN_VALUE)
                if (id == Long.MIN_VALUE) return
                goAsyncWork { app.container.engine.cancelSnooze(id) }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "hu.ulpifex.fulesebreszto.action.FIRE"
        const val ACTION_CANCEL_SNOOZE = "hu.ulpifex.fulesebreszto.action.CANCEL_SNOOZE"
    }
}

/**
 * Restores all alarms after boot (also before first unlock thanks to direct-boot storage),
 * app updates, manual clock changes, time-zone changes and exact-alarm permission grants.
 * It never starts a foreground service (forbidden after BOOT_COMPLETED on Android 15+);
 * an alarm missed by a few minutes is re-armed through AlarmManager instead.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val app = context.applicationContext as FulesApp
        goAsyncWork { app.container.engine.rescheduleAll(action.substringAfterLast('.')) }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.goAsyncWork(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } finally {
            pending.finish()
        }
    }
}

/** Bridges the gap between the alarm broadcast and the service taking its own wake lock. */
object StartupWakeLock {
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        if (lock?.isHeld == true) return
        lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FulesEbreszto:startup")
            .apply {
                setReferenceCounted(false)
                acquire(30_000)
            }
    }

    @Synchronized
    fun release() {
        lock?.let { if (it.isHeld) it.release() }
        lock = null
    }
}
