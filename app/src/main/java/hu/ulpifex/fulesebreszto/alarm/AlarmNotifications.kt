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
import hu.ulpifex.fulesebreszto.domain.HuFormat
import hu.ulpifex.fulesebreszto.domain.MissedRing
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

    fun createChannels() {
        val ring = NotificationChannel(CH_RING, "Aktív ébresztés", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Teljes képernyős ébresztés. Hangot csak az alkalmazás saját, fejhallgatóra irányított lejátszója ad."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val snooze = NotificationChannel(CH_SNOOZE, "Szundi", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Szundizó ébresztések"
            setSound(null, null)
            enableVibration(false)
        }
        val info = NotificationChannel(CH_INFO, "Elmaradt és lejárt ébresztések", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tájékoztatás, ha egy ébresztés nem tudott megszólalni vagy automatikusan leállt"
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

    fun ringNotification(title: String, text: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, REQ_RING_ACTIVITY,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CH_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
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
            .addAction(0, "Leállítás", serviceAction(AlarmRingService.ACTION_DISMISS, REQ_DISMISS))
            .addAction(0, "Szundi", serviceAction(AlarmRingService.ACTION_SNOOZE, REQ_SNOOZE))
            .build()
    }

    fun updateRing(title: String, text: String) {
        if (canPost()) manager.notify(ID_RING, ringNotification(title, text))
    }

    override fun snoozed(alarm: Alarm, until: Long) {
        if (!canPost()) return
        val at = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault())
        val cancel = PendingIntent.getBroadcast(
            context, idFor(ID_SNOOZE_BASE, alarm.id),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_CANCEL_SNOOZE)
                .putExtra(AndroidAlarmClock.EXTRA_ALARM_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_SNOOZE)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Szundi – ${alarm.label}")
            .setContentText("Újra szól: ${HuFormat.time(at.hour, at.minute)}")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(0, "Szundi leállítása", cancel)
            .build()
        manager.notify(idFor(ID_SNOOZE_BASE, alarm.id), n)
    }

    override fun clearSnoozed(alarmId: Long) {
        manager.cancel(idFor(ID_SNOOZE_BASE, alarmId))
    }

    override fun missed(missed: MissedRing) {
        if (!canPost()) return
        val at = Instant.ofEpochMilli(missed.occurrence).atZone(ZoneId.systemDefault())
        val what = if (missed.isSnooze) "szundi" else "ébresztés"
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Elmaradt $what: ${HuFormat.time(at.hour, at.minute)}")
            .setContentText("„${missed.alarm.label}” nem szólalt meg (kikapcsolt telefon vagy időátállítás).")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(idFor(ID_INFO_BASE, missed.alarm.id), n)
    }

    fun timedOut(label: String, minutes: Int, alarmId: Long) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Az ébresztés automatikusan leállt")
            .setContentText("„$label” $minutes perc után leállt, mert nem kapcsoltad ki.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(idFor(ID_INFO_BASE, alarmId), n)
    }

    fun failedToStart(label: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Az ébresztést a rendszer nem engedte elindítani")
            .setContentText("„$label” – ellenőrizd a jogosultságokat a Beállításokban.")
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
