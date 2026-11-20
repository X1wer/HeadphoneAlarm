package hu.ulpifex.fulesebreszto.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.ResolvedEnd
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.VolumeCurve
import hu.ulpifex.fulesebreszto.integration.ZengoRequest
import hu.ulpifex.fulesebreszto.integration.ZengoSession

/**
 * Plays a Zengő playlist alarm inside Zengő itself (Zengő 1.8.0+), through Zengő's MediaSession.
 * The music then lives in Zengő: the alarm can be closed while the list keeps playing ([handOver]).
 *
 * Headphone alarms stay fail-closed as far as another app's player allows: Zengő starts the list
 * muted with the allowed headphones as its preferred output, and it is made audible only while the
 * platform's media route (Android 13+) consists of allowed headphones only. Removing the headphones,
 * BECOMING_NOISY or any doubt about the route mutes and pauses Zengő at once and the alarm vibrates.
 * Before Android 13 the route of another app's player cannot be checked, so headphone alarms play
 * the list in this app instead (as before). Loud alarms play on Zengő's alarm stream without a check.
 *
 * Whenever Zengő cannot do the job (not installed, older than 1.8.0, no permission, it does not start
 * playing, it disconnects), the alarm falls back to [localPlayer] with the same list. Main thread only.
 */
class ZengoAlarmPlayer(
    private val context: Context,
    private val request: ZengoRequest,
    private val loud: Boolean,
    private val allowedKeys: () -> Set<String>,
    private val log: (String) -> Unit,
    private val listener: (AudioPathState) -> Unit,
    /** The in-app player (headphone or speaker), used as fallback and for the built-in tone at the end. */
    private val localPlayer: () -> AlarmAudio,
) : AlarmAudio {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private var running = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var local: AlarmAudio? = null
    private var fallbackSource: AlarmSoundSource? = null
    private var volumePercent = 0
    private var gain = 0f
    private var fadeMs = 0L

    private var playPending = false
    private var playing = false // Zengő accepted ALARM_PLAY for the current target
    private var playSentAt = 0L
    private var resumeNext = request.preferResume
    private var targetId: Int? = null
    private var audible = false
    private var everAudible = false
    private var pausedByUs = false
    /** Zengő has been seen playing since the last ALARM_PLAY (older state from before must not count). */
    private var seenPlaying = false
    private var notPlayingSince = 0L
    private var endedAtItem = false
    private var listEnded = false
    private var monitorsOn = false
    private var lastReported: AudioPathState? = null

    override val isRunning: Boolean get() = running

    /** True while the list is audibly playing in Zengő: the alarm may close and leave the music on. */
    val canHandOver: Boolean
        get() = running && local == null && audible && controller?.isPlaying == true

    /** True while Zengő (not this app) is the one playing the list. */
    val playsInZengo: Boolean get() = running && local == null && playing

    override fun start(source: AlarmSoundSource, volumePercent: Int, fadeInMs: Long, maxDurationMs: Long) {
        running = true
        fallbackSource = source
        this.volumePercent = volumePercent
        gain = VolumeCurve.gain(volumePercent)
        fadeMs = fadeInMs
        if (!loud && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            useLocal("route of another app's player cannot be verified before Android 13")
            return
        }
        report(AudioPathState.Checking)
        connect()
    }

    override fun stop() {
        if (!running && controller == null && local == null) return
        running = false
        handler.removeCallbacksAndMessages(null)
        local?.stop()
        local = null
        unregisterMonitors()
        releaseController(finish = true, keepPlaying = false)
    }

    /** Closes the alarm but leaves the list playing in Zengő; without Zengő playing it simply stops. */
    fun handOver() {
        if (!canHandOver) {
            stop()
            return
        }
        log("zengo: alarm closed, music keeps playing")
        running = false
        handler.removeCallbacksAndMessages(null)
        unregisterMonitors()
        releaseController(finish = true, keepPlaying = true)
    }

    // --- Connection ---

    private fun connect() {
        val token = SessionToken(context, ZengoSession.component(request.app))
        val future = MediaController.Builder(context, token)
            .setApplicationLooper(Looper.getMainLooper())
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (running && local == null) useLocal("zengo disconnected")
                }
            })
            .buildAsync()
        controllerFuture = future
        handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        future.addListener({
            handler.removeCallbacks(connectTimeout)
            val c = runCatching { future.get() }.getOrNull()
            when {
                !running || local != null -> c?.release()
                c == null -> useLocal("zengo session unavailable")
                !c.isSessionCommandAvailable(ZengoSession.play) -> {
                    c.release()
                    useLocal("zengo offers no alarm playback (older than 1.8.0 or no permission)")
                }
                else -> {
                    controller = c
                    c.addListener(events)
                    registerMonitors()
                    log("zengo connected (${request.app.packageName})")
                    evaluate("connected")
                }
            }
        }, mainExecutor)
    }

    private val connectTimeout = Runnable {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        if (running && local == null) useLocal("zengo did not answer in time")
    }

    private val events = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) endedAtItem = true
        }

        override fun onEvents(player: Player, events: Player.Events) {
            evaluate("zengo event")
        }
    }

    // --- Decisions ---

    private fun evaluate(trigger: String) {
        if (!running || local != null || listEnded) return
        val c = controller ?: return
        if (playing && !seenPlaying && c.isPlaying) {
            // From here on an "ended" state is this run's own end of the list.
            seenPlaying = true
            endedAtItem = false
        }
        if (c.isPlaying) notPlayingSince = 0L
        if (loud) evaluateLoud(c) else evaluateHeadphones(c, trigger)
    }

    private fun evaluateLoud(c: MediaController) {
        if (!playing) {
            if (!playPending) sendPlay(c, deviceId = 0)
            report(AudioPathState.Checking)
            return
        }
        if (!c.isPlaying) {
            onNotPlaying(c)
            return
        }
        if (!audible) {
            audible = true
            everAudible = true
            c.sendCustomCommand(ZengoSession.volume, ZengoSession.volumeArgs(gain, fadeMs))
        }
        report(
            AudioPathState.Audible(
                deviceName = "",
                deviceKind = OutputKind.BUILTIN_SPEAKER,
                systemVolumeZero = audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0,
                usedFallbackSound = false,
                nowPlaying = nowPlaying(c),
                speaker = true,
            ),
        )
    }

    private fun evaluateHeadphones(c: MediaController, trigger: String) {
        val connected = AudioDevices.connectedOutputs(audioManager).map { it.first }
        val policy = OutputPolicy(allowedKeys())
        val target = policy.chooseTarget(connected)
        if (target == null) {
            pauseZengo("no allowed headphones ($trigger)")
            report(AudioPathState.Silent(if (everAudible) SilentReason.OUTPUT_LOST else SilentReason.NO_HEADPHONES))
            return
        }
        if (!playing || target.id != targetId) {
            if (!playPending) {
                if (playing) mute()
                sendPlay(c, target.id)
            }
            if (!everAudible) report(AudioPathState.Checking)
            return
        }
        if (!c.isPlaying) {
            mute()
            onNotPlaying(c)
            return
        }
        // Zengő plays (muted) with the target as its preferred output: the platform's media route
        // must now consist of allowed headphones only.
        val strategy = AudioDevices.mediaStrategyDevices(audioManager)
        val snapshot = RouteSnapshot(
            connected = connected,
            routed = strategy.orEmpty(),
            strategy = strategy,
            audioModeNormal = audioManager.mode == AudioManager.MODE_NORMAL,
        )
        when (val verdict = policy.verify(target, snapshot)) {
            is RouteVerdict.Verified -> {
                if (!audible) {
                    audible = true
                    val fade = if (everAudible) RECOVERY_FADE_MS else fadeMs
                    everAudible = true
                    log("zengo route verified: ${target.kind}")
                    c.sendCustomCommand(ZengoSession.volume, ZengoSession.volumeArgs(gain, fade))
                }
                report(
                    AudioPathState.Audible(
                        deviceName = target.name,
                        deviceKind = target.kind,
                        systemVolumeZero = AudioDevices.mediaVolumePercent(audioManager) == 0,
                        usedFallbackSound = false,
                        nowPlaying = nowPlaying(c),
                    ),
                )
            }
            is RouteVerdict.Unverified -> {
                mute()
                val inGrace = !everAudible && SystemClock.elapsedRealtime() - playSentAt < START_TIMEOUT_MS
                report(
                    when {
                        inGrace -> AudioPathState.Checking
                        verdict.reason == UnverifiedReason.TARGET_DISCONNECTED -> AudioPathState.Silent(SilentReason.OUTPUT_LOST)
                        else -> AudioPathState.Silent(SilentReason.ROUTE_UNVERIFIED, verdict.reason)
                    },
                )
            }
        }
    }

    /** Zengő is not (or no longer) playing: list end, start-up, an error, or a pause from elsewhere. */
    private fun onNotPlaying(c: MediaController) {
        val now = SystemClock.elapsedRealtime()
        if (notPlayingSince == 0L) notPlayingSince = now
        val starting = now - playSentAt < START_TIMEOUT_MS
        // Zengő skips a broken song by itself; only an error or a stall that lasts counts.
        val stalled = c.playerError != null || (c.playWhenReady && c.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        when {
            pausedByUs -> Unit
            // Until Zengő has played in this run, its state may still be the one from before the command.
            !seenPlaying -> if (starting) {
                if (!everAudible) report(AudioPathState.Checking)
            } else {
                useLocal("zengo did not start playing (${c.playerError?.errorCodeName ?: "state ${c.playbackState}"})")
            }
            c.playbackState == Player.STATE_ENDED || endedAtItem -> onListEnded(c)
            stalled -> if (now - notPlayingSince >= RECOVER_TIMEOUT_MS) {
                useLocal("zengo stopped playing (${c.playerError?.errorCodeName ?: "state ${c.playbackState}"})")
            }
            // Paused in Zengő itself (its notification) or by a call / another app's audio focus.
            else -> report(AudioPathState.Silent(SilentReason.ZENGO_PAUSED))
        }
    }

    private fun onListEnded(c: MediaController) {
        listEnded = true
        audible = false
        log("zengo list ended, end=${request.plan.end}")
        when (request.plan.end) {
            ResolvedEnd.BUILTIN_TONE -> {
                c.sendCustomCommand(ZengoSession.finish, ZengoSession.finishArgs(keepPlaying = false))
                startLocal(SingleSoundSource(BuiltInSounds.FALLBACK.ref, loops = true, fallback = true))
            }
            ResolvedEnd.STOP_ALARM -> report(AudioPathState.SourceEnded(stopAlarm = true))
            else -> report(AudioPathState.SourceEnded(stopAlarm = false))
        }
    }

    // --- Commands to Zengő ---

    private fun sendPlay(c: MediaController, deviceId: Int) {
        playPending = true
        audible = false
        seenPlaying = false
        endedAtItem = false
        targetId = deviceId.takeIf { it != 0 }
        val resume = resumeNext
        resumeNext = true // any later restart (reconnected headphones) continues where it stopped
        playSentAt = SystemClock.elapsedRealtime()
        val result = c.sendCustomCommand(ZengoSession.play, request.playArgs(loud, deviceId, resume))
        result.addListener({
            playPending = false
            val code = runCatching { result.get().resultCode }.getOrDefault(SessionError.ERROR_UNKNOWN)
            if (!running || local != null) return@addListener
            if (code == SessionResult.RESULT_SUCCESS) {
                playing = true
                pausedByUs = false
                playSentAt = SystemClock.elapsedRealtime()
                log("zengo playing (resume=$resume, device=$deviceId)")
                evaluate("play accepted")
            } else {
                useLocal("zengo refused the list (code $code)")
            }
        }, mainExecutor)
    }

    private fun mute() {
        if (!audible) return
        audible = false
        controller?.sendCustomCommand(ZengoSession.volume, ZengoSession.volumeArgs(0f, 0L))
    }

    /** Mutes and pauses Zengő (no allowed output); the next evaluation restarts it where it stopped. */
    private fun pauseZengo(reason: String) {
        val c = controller ?: return
        if (!playing && !playPending) return
        mute()
        c.sendCustomCommand(ZengoSession.volume, ZengoSession.volumeArgs(0f, 0L))
        if (c.playWhenReady) c.pause()
        if (playing) log("zengo paused: $reason")
        pausedByUs = true
        playing = false
        targetId = null
    }

    private fun releaseController(finish: Boolean, keepPlaying: Boolean) {
        handler.removeCallbacks(connectTimeout)
        val pending = controllerFuture
        controllerFuture = null
        val c = controller
        if (c == null) {
            pending?.let(MediaController::releaseFuture)
            return
        }
        controller = null
        c.removeListener(events)
        if (!finish || (!playing && !playPending && !pausedByUs && !listEnded)) {
            c.release()
            return
        }
        // Release only after Zengő has handled the finish, so it cannot be mistaken for a crash.
        val done = c.sendCustomCommand(ZengoSession.finish, ZengoSession.finishArgs(keepPlaying))
        val release = Runnable { c.release() }
        done.addListener(release, mainExecutor)
        handler.postDelayed(release, FINISH_TIMEOUT_MS)
    }

    // --- Fallback to the in-app player ---

    private fun useLocal(reason: String) {
        if (!running || local != null) return
        log("zengo: $reason – the alarm plays the list itself")
        unregisterMonitors()
        releaseController(finish = playing || playPending, keepPlaying = false)
        playing = false
        playPending = false
        audible = false
        startLocal(fallbackSource ?: SingleSoundSource(BuiltInSounds.FALLBACK.ref, fallback = true))
    }

    private fun startLocal(source: AlarmSoundSource) {
        unregisterMonitors()
        val player = localPlayer()
        local = player
        lastReported = null
        player.start(source, volumePercent, if (everAudible) RECOVERY_FADE_MS else fadeMs)
    }

    // --- Route monitoring (headphone alarms) ---

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == targetId }) pauseZengo("target removed")
            evaluate("devices removed")
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            evaluate("devices added")
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Sent before the platform falls back to the speaker.
                pauseZengo("becoming noisy")
                evaluate("noisy")
            }
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!running || local != null) return
            evaluate("watchdog")
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    private fun registerMonitors() {
        if (monitorsOn) return
        monitorsOn = true
        if (!loud) {
            audioManager.registerAudioDeviceCallback(deviceCallback, handler)
            ContextCompat.registerReceiver(
                context, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun unregisterMonitors() {
        handler.removeCallbacks(watchdog)
        if (!monitorsOn) return
        monitorsOn = false
        if (!loud) {
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { context.unregisterReceiver(noisyReceiver) }
        }
    }

    private fun nowPlaying(c: MediaController): String? {
        val meta = c.mediaMetadata
        val title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val artist = meta.artist?.toString()
        return if (artist.isNullOrBlank()) title else "$title – $artist"
    }

    private fun report(state: AudioPathState) {
        if (state == lastReported) return
        lastReported = state
        listener(state)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000L
        const val START_TIMEOUT_MS = 6_000L
        const val RECOVER_TIMEOUT_MS = 4_000L
        const val FINISH_TIMEOUT_MS = 1_500L
        const val WATCHDOG_MS = 250L
        const val RECOVERY_FADE_MS = 1_500L
    }
}
