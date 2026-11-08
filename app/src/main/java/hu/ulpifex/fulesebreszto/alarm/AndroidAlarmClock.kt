package hu.ulpifex.fulesebreszto.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import hu.ulpifex.fulesebreszto.domain.TriggerKind
import hu.ulpifex.fulesebreszto.ui.MainActivity

/**
 * AlarmManager adapter. Every trigger uses setAlarmClock(), which is exact, wakes the device
 * from Doze, shows the alarm icon in the status bar and allows starting the ringing
 * foreground service from the resulting broadcast.
 */
class AndroidAlarmClock(
    private val context: Context,
    private val log: (String) -> Unit,
) : AlarmClockGateway {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun arm(alarmId: Long, kind: TriggerKind, fireAt: Long, occurrence: Long) {
        val operation = triggerIntent(alarmId, kind, occurrence, PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            if (!canScheduleExact()) throw SecurityException("exact alarms not permitted")
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, showIntent()), operation)
        } catch (e: SecurityException) {
            // Fallback keeps the alarm alive, but it may be delayed by Doze. The UI warns about it.
            log("exact alarm refused, using inexact fallback")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
        }
    }

    override fun cancel(alarmId: Long, kind: TriggerKind) {
        val existing = triggerIntent(alarmId, kind, 0, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun triggerIntent(alarmId: Long, kind: TriggerKind, occurrence: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            // The data URI gives each (alarm, kind) pair its own PendingIntent identity.
            .setData(Uri.parse("fulesebreszto://alarm/$alarmId/${kind.name.lowercase()}"))
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_KIND, kind.name)
            .putExtra(EXTRA_OCCURRENCE, occurrence)
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_OCCURRENCE = "occurrence"
    }
}
