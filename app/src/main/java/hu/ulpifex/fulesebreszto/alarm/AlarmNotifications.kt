package hu.ulpifex.fulesebreszto.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.MissedRing
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.ui.MainActivity
import hu.ulpifex.fulesebreszto.ui.ring.AlarmActivity
import java.time.Instant
import java.time.ZoneId

/**
 * Notification channels and builders. No channel ever plays a sound or vibrates by itself:
 * audible output is produced exclusively by [hu.ulpifex.fulesebreszto.audio.SafeHeadphonePlayer].
 */
class AlarmNotifications(private val context: Context) : AlarmNotifier {

    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Texts are read in the app language (which may differ from the system on Android 10–12). */
    private fun text(id: Int, vararg args: Any): String = AppLocale.localized(context).getString(id, *args)

    private fun clock(epochMs: Long): String {
        val at = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        return AppLocale.timeFormat(context).time(at.hour, at.minute)
    }

    /** Creates or renames the channels (called at every start, so names follow the language). */
    fun createChannels() {
        val ring = NotificationChannel(CH_RING, text(R.string.ch_ring_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = text(R.string.ch_ring_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val snooze = NotificationChannel(CH_SNOOZE, text(R.string.ch_snooze_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = text(R.string.ch_snooze_desc)
            setSound(null, null)
            enableVibration(false)
        }
        val info = NotificationChannel(CH_INFO, text(R.string.ch_info_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = text(R.string.ch_info_desc)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(listOf(ring, snooze, info))
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    /** Notification shown the moment the service starts, before the alarm is loaded. */
    fun initialRingNotification(): Notification =
        ringNotification(context.getString(R.string.app_name), text(R.string.notif_ring_checking))

    fun ringNotification(title: String, body: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, REQ_RING_ACTIVITY,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CH_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // No setSilent(): NotificationCompat implements it with a summary-only alerting group,
            // which makes the system suppress the full-screen intent. The channel itself has no
            // sound and no vibration, so the notification is silent anyway.
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, text(R.string.ring_dismiss), serviceAction(AlarmRingService.ACTION_DISMISS, REQ_DISMISS))
            .addAction(0, text(R.string.ring_snooze), serviceAction(AlarmRingService.ACTION_SNOOZE, REQ_SNOOZE))
            .build()
    }

    fun updateRing(alarmTime: String, label: String, status: String) {
        if (!canPost()) return
        manager.notify(
            ID_RING,
            ringNotification(text(R.string.notif_ring_title, alarmTime), text(R.string.notif_ring_text, label, status)),
        )
    }

    override fun snoozed(alarm: Alarm, until: Long) {
        if (!canPost()) return
        val cancel = PendingIntent.getBroadcast(
            context, idFor(ID_SNOOZE_BASE, alarm.id),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_CANCEL_SNOOZE)
                .putExtra(AndroidAlarmClock.EXTRA_ALARM_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_SNOOZE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(text(R.string.notif_snooze_title, alarm.label))
            .setContentText(text(R.string.notif_snooze_text, clock(until)))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(0, text(R.string.notif_cancel_snooze), cancel)
            .build()
        manager.notify(idFor(ID_SNOOZE_BASE, alarm.id), n)
    }

    override fun clearSnoozed(alarmId: Long) {
        manager.cancel(idFor(ID_SNOOZE_BASE, alarmId))
    }

    override fun missed(missed: MissedRing) {
        if (!canPost()) return
        val title = if (missed.isSnooze) R.string.notif_missed_snooze else R.string.notif_missed_alarm
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(text(title, clock(missed.occurrence)))
            .setContentText(text(R.string.notif_missed_text, missed.alarm.label))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(idFor(ID_INFO_BASE, missed.alarm.id), n)
    }

    fun timedOut(label: String, minutes: Int, alarmId: Long) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(text(R.string.notif_timed_out_title))
            .setContentText(AppLocale.localized(context).resources.getQuantityString(R.plurals.notif_timed_out_text, minutes, label, minutes))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(idFor(ID_INFO_BASE, alarmId), n)
    }

    fun failedToStart() {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(text(R.string.notif_start_failed_title))
            .setContentText(text(R.string.notif_start_failed_text, text(R.string.default_alarm_label)))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(ID_START_FAILED, n)
    }

    private fun serviceAction(action: String, request: Int): PendingIntent = PendingIntent.getService(
        context, request,
        Intent(context, AlarmRingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, REQ_OPEN_APP,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun idFor(base: Int, alarmId: Long): Int = base + (alarmId.mod(100_000L)).toInt()

    companion object {
        const val CH_RING = "ring_silent_v1"
        const val CH_SNOOZE = "snooze_v1"
        const val CH_INFO = "info_v1"

        const val ID_RING = 1207
        private const val ID_START_FAILED = 1208
        private const val ID_SNOOZE_BASE = 200_000
        private const val ID_INFO_BASE = 400_000

        private const val REQ_RING_ACTIVITY = 1
        private const val REQ_DISMISS = 11
        private const val REQ_SNOOZE = 12
        private const val REQ_OPEN_APP = 13
    }
}
