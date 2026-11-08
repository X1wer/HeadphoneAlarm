package hu.ulpifex.fulesebreszto.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.SoundResolver
import hu.ulpifex.fulesebreszto.domain.VolumeCurve

/** What the player can currently guarantee about the audio path. */
sealed interface AudioPathState {
    data object Checking : AudioPathState

    data class Audible(
        val deviceName: String,
        val deviceKind: OutputKind,
        val systemVolumeZero: Boolean,
        val usedFallbackSound: Boolean,
    ) : AudioPathState

    data class Silent(val reason: SilentReason, val detail: UnverifiedReason? = null) : AudioPathState
}

/**
 * Plays a sound only on a verified, allowed headphone output.
 *
 * The player is always created muted and bound to the chosen device with setPreferredDevice().
 * It becomes audible only after the reported route (getRoutedDevice(s)) and, on API 33+, the
 * platform's media strategy both prove that nothing but allowed outputs are involved. Device
 * removal, BECOMING_NOISY, routing changes and focus loss mute it synchronously first and
 * re-evaluate afterwards. A 250 ms watchdog re-checks the route while a player exists.
 *
 * Must be used from the main thread.
 */
class SafeHeadphonePlayer(
    private val context: Context,
    private val allowedKeys: () -> Set<String>,
    private val log: (String) -> Unit,
    private val listener: (AudioPathState) -> Unit,
    /** Called when playback ends by itself (max duration reached or a non-looping sound finished). */
    private val onAutoStop: () -> Unit = {},
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var player: MediaPlayer? = null
    private var targetId: Int? = null
    private var target: OutputDevice? = null
    private var audible = false
    private var everAudible = false
    private var mutedSince = 0L
    private var currentGain = 0f
    private var targetGain = 0f
    private var fadeMs = 0L
    private var sound: SoundRef = BuiltInSounds.DEFAULT.ref
    private var forceFallback = false
    private var usingFallback = false
    private var fatalSoundError = false
    private var focusLost = false
    private var focusRequest: AudioFocusRequest? = null
    private var lastReported: AudioPathState? = null
    private var loop = true
    private var stopAt = 0L

    private val routingListener = AudioRouting.OnRoutingChangedListener {
        // Mute first, then decide again on the new route.
        muteNow("routing changed")
        evaluate("routing")
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == targetId }) {
                muteNow("target removed")
                releasePlayer()
            }
            evaluate("devices removed")
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            evaluate("devices added")
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Sent before the platform falls back to the speaker: drop the player entirely.
                muteNow("becoming noisy")
                releasePlayer()
                evaluate("noisy")
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLost = true
                muteNow("focus lost")
                evaluate("focus lost")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                evaluate("focus gained")
            }
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            if (stopAt > 0 && SystemClock.elapsedRealtime() >= stopAt) {
                stop()
                onAutoStop()
                return
            }
            evaluate("watchdog")
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    private val fadeStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!audible) return
            val step = if (fadeMs <= 0) 1f else targetGain * FADE_TICK_MS / fadeMs
            currentGain = (currentGain + step.coerceAtLeast(0.002f)).coerceAtMost(targetGain)
            runCatching { p.setVolume(currentGain, currentGain) }
            if (currentGain < targetGain) handler.postDelayed(this, FADE_TICK_MS)
        }
    }

    val isRunning: Boolean get() = running

    /**
     * @param maxDurationMs stop automatically after this time (0 = until [stop]).
     */
    fun start(sound: SoundRef, volumePercent: Int, fadeInMs: Long, loop: Boolean = true, maxDurationMs: Long = 0) {
        if (running) stop()
        running = true
        this.sound = sound
        this.loop = loop
        targetGain = VolumeCurve.gain(volumePercent)
        fadeMs = fadeInMs
        forceFallback = false
        fatalSoundError = false
        everAudible = false
        lastReported = null
        stopAt = if (maxDurationMs > 0) SystemClock.elapsedRealtime() + maxDurationMs else 0
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        ContextCompat.registerReceiver(
            context, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        requestFocus()
        evaluate("start")
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    fun stop() {
        if (!running && player == null) return
        running = false
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(fadeStep)
        muteNow("stop")
        releasePlayer()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { context.unregisterReceiver(noisyReceiver) }
        abandonFocus()
        target = null
    }

    private fun evaluate(trigger: String) {
        if (!running) return
        if (fatalSoundError) {
            report(AudioPathState.Silent(SilentReason.SOUND_FAILED))
            return
        }
        val pairs = AudioDevices.connectedOutputs(audioManager)
        val connected = pairs.map { it.first }
        val policy = OutputPolicy(allowedKeys())
        val snapshot = RouteSnapshot(
            connected = connected,
            routed = currentRoute(),
            strategy = AudioDevices.mediaStrategyDevices(audioManager),
            audioModeNormal = audioManager.mode == AudioManager.MODE_NORMAL,
            focusLost = focusLost,
        )
        when (val decision = AudioPathEvaluator(policy).decide(snapshot, targetId)) {
            PathDecision.NoTarget -> {
                muteNow("no target")
                releasePlayer()
                report(AudioPathState.Silent(if (everAudible) SilentReason.OUTPUT_LOST else SilentReason.NO_HEADPHONES))
            }

            is PathDecision.NeedPlayer -> {
                muteNow("new target")
                releasePlayer()
                val info = pairs.first { it.first.id == decision.target.id }.second
                if (createMutedPlayer(decision.target, info)) {
                    log("player created for ${decision.target.kind} ($trigger)")
                    report(AudioPathState.Checking)
                } else {
                    report(AudioPathState.Silent(SilentReason.SOUND_FAILED))
                }
            }

            is PathDecision.Audible -> {
                if (!audible) {
                    audible = true
                    val firstTime = !everAudible
                    everAudible = true
                    currentGain = 0f
                    if (!firstTime) fadeMs = RECOVERY_FADE_MS
                    log("route verified: ${decision.target.kind}")
                    handler.removeCallbacks(fadeStep)
                    handler.post(fadeStep)
                }
                report(
                    AudioPathState.Audible(
                        deviceName = decision.target.name,
                        deviceKind = decision.target.kind,
                        systemVolumeZero = AudioDevices.mediaVolumePercent(audioManager) == 0,
                        usedFallbackSound = usingFallback,
                    ),
                )
            }

            is PathDecision.StayMuted -> {
                muteNow("unverified: ${decision.reason}")
                val waited = SystemClock.elapsedRealtime() - mutedSince
                if (everAudible || waited >= CHECK_GRACE_MS) {
                    val reason = if (decision.reason == UnverifiedReason.TARGET_DISCONNECTED) {
                        SilentReason.OUTPUT_LOST
                    } else {
                        SilentReason.ROUTE_UNVERIFIED
                    }
                    report(AudioPathState.Silent(reason, decision.reason))
                } else {
                    report(AudioPathState.Checking)
                }
            }
        }
    }

    private fun currentRoute(): List<OutputDevice> {
        val p = player ?: return emptyList()
        if (Build.VERSION.SDK_INT >= 36) {
            val all = runCatching { p.routedDevices }.getOrNull().orEmpty()
            if (all.isNotEmpty()) return all.map(AudioDevices::toOutput)
        }
        return listOfNotNull(runCatching { p.routedDevice }.getOrNull()?.let(AudioDevices::toOutput))
    }

    private fun createMutedPlayer(device: OutputDevice, info: AudioDeviceInfo): Boolean {
        val resolved = if (forceFallback) {
            SoundResolver.resolve(BuiltInSounds.FALLBACK.ref) { false }.copy(usedFallback = true)
        } else {
            SoundResolver.resolve(sound) { canOpen(it.uri) }
        }
        if (resolved.usedFallback && !usingFallback) log("custom sound unavailable, using built-in fallback")
        usingFallback = resolved.usedFallback
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(AudioDevices.MEDIA_ATTRIBUTES)
            setSource(mp, resolved.ref)
            mp.isLooping = loop
            mp.setVolume(0f, 0f)
            if (!mp.setPreferredDevice(info)) log("setPreferredDevice refused")
            mp.setOnErrorListener { _, what, extra ->
                handler.post { onPlayerError(what, extra) }
                true
            }
            mp.setOnCompletionListener {
                handler.post {
                    if (!loop && running) {
                        stop()
                        onAutoStop()
                    }
                }
            }
            mp.prepare()
            mp.addOnRoutingChangedListener(routingListener, handler)
            mp.start()
            player = mp
            targetId = device.id
            target = device
            audible = false
            mutedSince = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            log("player setup failed: ${e.javaClass.simpleName}")
            runCatching { mp.release() }
            if (!resolved.usedFallback) {
                forceFallback = true
                createMutedPlayer(device, info)
            } else {
                fatalSoundError = true
                false
            }
        }
    }

    private fun onPlayerError(what: Int, extra: Int) {
        log("player error $what/$extra")
        muteNow("player error")
        releasePlayer()
        if (usingFallback) fatalSoundError = true else forceFallback = true
        evaluate("player error")
    }

    private fun setSource(mp: MediaPlayer, ref: SoundRef) {
        when (ref) {
            is SoundRef.BuiltIn -> context.resources.openRawResourceFd(rawResFor(ref.key)).use { afd ->
                mp.setDataSource(afd)
            }
            is SoundRef.Custom -> mp.setDataSource(context, Uri.parse(ref.uri))
        }
    }

    private fun canOpen(uri: String): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun muteNow(reason: String) {
        handler.removeCallbacks(fadeStep)
        if (audible) log("muted: $reason")
        if (audible || currentGain > 0f) mutedSince = SystemClock.elapsedRealtime()
        audible = false
        currentGain = 0f
        runCatching { player?.setVolume(0f, 0f) }
    }

    private fun releasePlayer() {
        val p = player ?: run {
            targetId = null
            return
        }
        runCatching { p.setVolume(0f, 0f) }
        runCatching { p.removeOnRoutingChangedListener(routingListener) }
        runCatching { p.stop() }
        runCatching { p.release() }
        player = null
        targetId = null
        audible = false
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioDevices.MEDIA_ATTRIBUTES)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = request
        focusLost = audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focusLost) log("audio focus not granted")
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
        focusLost = false
    }

    private fun report(state: AudioPathState) {
        if (state == lastReported) return
        lastReported = state
        listener(state)
    }

    companion object {
        private const val WATCHDOG_MS = 250L
        private const val FADE_TICK_MS = 50L
        private const val RECOVERY_FADE_MS = 400L

        /** How long a fresh player may stay unverified before we report "silent". */
        private const val CHECK_GRACE_MS = 1_500L

        fun rawResFor(key: String): Int = when (key) {
            BuiltInSounds.SOFT.key -> R.raw.soft_melody
            BuiltInSounds.BEEP.key -> R.raw.simple_beep
            BuiltInSounds.CHIME.key -> R.raw.morning_chime
            else -> R.raw.classic_alarm
        }
    }
}
