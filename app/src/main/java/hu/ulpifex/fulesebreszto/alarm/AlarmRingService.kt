package hu.ulpifex.fulesebreszto.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.FulesApp
import hu.ulpifex.fulesebreszto.audio.AudioPathState
import hu.ulpifex.fulesebreszto.audio.SafeHeadphonePlayer
import hu.ulpifex.fulesebreszto.audio.VibrationController
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.AlarmStateMachine
import hu.ulpifex.fulesebreszto.domain.RingEffect
import hu.ulpifex.fulesebreszto.domain.RingEvent
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.TriggerKind
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.ui.ringStatusText
import hu.ulpifex.fulesebreszto.ui.AppVisibility
import hu.ulpifex.fulesebreszto.ui.ring.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * Foreground service that owns the ringing alarm. It holds exactly one [RingSession];
 * alarms that fire while it rings are merged into it instead of starting a second player.
 */
class AlarmRingService : Service() {

    private val container: AppContainer get() = (application as FulesApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: RingSession? = null
    private var pendingTriggers = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER -> {
                // Drop the finished state of an earlier session so the alarm screen, which the
                // full-screen intent may open right now, waits for this session instead.
                if (session?.isRinging != true) RingSessionState.publish(null)
                enterForeground()
                handleTrigger(intent)
            }
            ACTION_DISMISS -> session?.dispatch(RingEvent.Dismiss) ?: stopIfIdle()
            ACTION_SNOOZE -> session?.dispatch(RingEvent.Snooze) ?: stopIfIdle()
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val notification = container.notifications.initialRingNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(AlarmNotifications.ID_RING, notification)
            return
        }
        try {
            startForeground(AlarmNotifications.ID_RING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: Exception) {
            // systemExempted requires the exact-alarm permission; fall back to mediaPlayback.
            container.log.log("systemExempted FGS refused (${e.javaClass.simpleName}), using mediaPlayback")
            startForeground(AlarmNotifications.ID_RING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
    }

    private fun handleTrigger(intent: Intent) {
        val id = intent.getLongExtra(AndroidAlarmClock.EXTRA_ALARM_ID, Long.MIN_VALUE)
        val kind = TriggerKind.entries.firstOrNull { it.name == intent.getStringExtra(AndroidAlarmClock.EXTRA_KIND) }
            ?: TriggerKind.MAIN
        val occurrence = intent.getLongExtra(AndroidAlarmClock.EXTRA_OCCURRENCE, 0L)
        pendingTriggers++
        scope.launch {
            val resolved = try {
                withContext(Dispatchers.IO) {
                    container.engine.onTrigger(id, kind, occurrence)?.let { it to container.devices.allowedKeysNow() }
                }
            } catch (e: Exception) {
                container.log.log("trigger handling failed: ${e.javaClass.simpleName}")
                null
            }
            pendingTriggers--
            val current = session
            when {
                resolved == null -> Unit
                current != null && current.isRinging -> current.merge(resolved.first)
                else -> startSession(resolved.first, resolved.second)
            }
            StartupWakeLock.release()
            stopIfIdle()
        }
    }

    private fun startSession(alarm: Alarm, allowedKeys: Set<String>) {
        acquireWakeLock(alarm.maxRingMinutes)
        val s = RingSession(
            context = this,
            primary = alarm,
            allowedKeys = allowedKeys,
            container = container,
            scope = scope,
            onChanged = { ui ->
                container.notifications.updateRing(ui.alarmTime, ui.label, ringStatusText(AppLocale.localized(this), ui))
            },
            onEnded = { onSessionEnded() },
        )
        session = s
        s.start()
        if (AppVisibility.isForeground) {
            runCatching {
                startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun onSessionEnded() {
        session = null
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (session?.isRinging == true || pendingTriggers > 0) return
        releaseWakeLock()
        StartupWakeLock.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock(maxRingMinutes: Int) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FulesEbreszto:ringing")
            .apply {
                setReferenceCounted(false)
                acquire((maxRingMinutes + 2) * 60_000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        session?.forceStop()
        session = null
        if (RingSessionState.isRinging) RingSessionState.publish(null)
        releaseWakeLock()
        StartupWakeLock.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TRIGGER = "hu.ulpifex.fulesebreszto.action.TRIGGER"
        const val ACTION_DISMISS = "hu.ulpifex.fulesebreszto.action.DISMISS"
        const val ACTION_SNOOZE = "hu.ulpifex.fulesebreszto.action.SNOOZE"

        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(action))
        }
    }
}

/**
 * One ringing episode. Executes the effects returned by [AlarmStateMachine]; nothing else
 * starts or stops sound or vibration.
 */
private class RingSession(
    context: Context,
    primary: Alarm,
    allowedKeys: Set<String>,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val onChanged: (RingUiState) -> Unit,
    private val onEnded: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val alarms = mutableListOf(primary)
    private val primary: Alarm get() = alarms.first()
    private val machine = AlarmStateMachine(primary.vibrateWithHeadphones)
    private val vibration = VibrationController(context)
    private val player = SafeHeadphonePlayer(
        context = context,
        allowedKeys = { allowedKeys },
        log = container.log::log,
        listener = { state -> handler.post { onAudio(state) } },
    )
    private val timeText = LocalTime.now().let { AppLocale.timeFormat(context).time(it.hour, it.minute) }
    private val timeout = Runnable { dispatch(RingEvent.Timeout) }
    private val engineJobs = mutableListOf<Job>()
    private var usedFallback = false
    private var finished: FinishKind? = null

    val isRinging: Boolean get() = machine.state.isRinging

    fun start() {
        container.log.log("ringing session started")
        dispatch(RingEvent.Trigger)
    }

    fun merge(alarm: Alarm) {
        if (alarms.none { it.id == alarm.id }) alarms += alarm
        container.log.log("alarm merged into active session (${alarms.size} total)")
        publish()
    }

    fun dispatch(event: RingEvent) {
        val effects = machine.handle(event)
        effects.forEach(::execute)
        publish()
    }

    fun forceStop() {
        handler.removeCallbacks(timeout)
        player.stop()
        vibration.stop()
    }

    private fun onAudio(state: AudioPathState) {
        if (!machine.state.isRinging) return
        when (state) {
            AudioPathState.Checking -> dispatch(RingEvent.AudioCheckStarted)
            is AudioPathState.Audible -> {
                usedFallback = state.usedFallbackSound
                dispatch(RingEvent.AudioVerified(state.deviceName, state.systemVolumeZero))
            }
            is AudioPathState.Silent -> dispatch(RingEvent.AudioUnavailable(state.reason))
        }
    }

    private fun execute(effect: RingEffect) {
        val ids = alarms.map { it.id }
        when (effect) {
            RingEffect.StartAudioCheck ->
                if (primary.volumePercent <= 0) {
                    handler.post { dispatch(RingEvent.AudioUnavailable(SilentReason.VOLUME_ZERO)) }
                } else {
                    player.start(primary.sound, primary.volumePercent, FADE_IN_MS)
                }
            RingEffect.StopAudio -> player.stop()
            RingEffect.StartVibration -> vibration.startAlarmPattern()
            RingEffect.StopVibration -> vibration.stop()
            RingEffect.ShowRingUi, RingEffect.CloseRingUi, RingEffect.CancelSnooze -> Unit
            RingEffect.ArmTimeout -> handler.postDelayed(timeout, primary.maxRingMinutes * 60_000L)
            RingEffect.CancelTimeout -> handler.removeCallbacks(timeout)
            RingEffect.ScheduleSnooze -> {
                finished = FinishKind.SNOOZED
                engineJobs += container.appScope.launch { container.engine.snooze(ids, primary.snoozeMinutes) }
            }
            RingEffect.CompleteDismiss -> {
                if (finished == null) finished = FinishKind.DISMISSED
                engineJobs += container.appScope.launch { container.engine.dismiss(ids) }
            }
            RingEffect.NotifyTimedOut -> {
                finished = FinishKind.TIMED_OUT
                container.notifications.timedOut(primary.label, primary.maxRingMinutes, primary.id)
            }
            RingEffect.StopService -> scope.launch {
                // Persist snooze/dismiss before the service is allowed to stop.
                engineJobs.joinAll()
                container.log.log("ringing session ended: $finished")
                onEnded()
            }
        }
    }

    private fun publish() {
        val ui = RingUiState(
            alarmIds = alarms.map { it.id },
            label = primary.label,
            alarmTime = timeText,
            state = machine.state,
            silentReason = machine.silentReason,
            audioDevice = machine.audioDevice,
            systemVolumeZero = machine.systemVolumeZero,
            usedFallbackSound = usedFallback,
            snoozeMinutes = primary.snoozeMinutes,
            finished = finished,
        )
        RingSessionState.publish(ui)
        if (finished == null) onChanged(ui)
    }

    private companion object {
        const val FADE_IN_MS = 5_000L
    }
}
