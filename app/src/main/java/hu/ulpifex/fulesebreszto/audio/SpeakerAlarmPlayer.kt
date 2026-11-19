package hu.ulpifex.fulesebreszto.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.VolumeCurve

/**
 * The player of a normal, loud alarm: the alarm stream (USAGE_ALARM), which Android plays on the
 * speaker and – when headphones are connected – on them as well, also in Do Not Disturb if alarms
 * are allowed. There is deliberately no route restriction here; the headphone-only guarantee is
 * the job of [SafeHeadphonePlayer]. Its loudness follows the phone's alarm volume, scaled by the
 * alarm's own volume setting.
 *
 * Must be used from the main thread.
 */
class SpeakerAlarmPlayer(
    private val context: Context,
    private val log: (String) -> Unit,
    private val listener: (AudioPathState) -> Unit,
) : AlarmAudio {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var source: AlarmSoundSource = SingleSoundSource(hu.ulpifex.fulesebreszto.domain.BuiltInSounds.DEFAULT.ref)
    private var running = false
    private var gain = 0f
    private var targetGain = 0f
    private var fadeMs = 0L
    private var firstSound = true
    private var focusRequest: AudioFocusRequest? = null

    private val fadeStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            val step = if (fadeMs <= 0) 1f else targetGain * FADE_TICK_MS / fadeMs
            gain = (gain + step.coerceAtLeast(0.002f)).coerceAtMost(targetGain)
            runCatching { p.setVolume(gain, gain) }
            if (gain < targetGain) handler.postDelayed(this, FADE_TICK_MS)
        }
    }

    override val isRunning: Boolean get() = running

    override fun start(source: AlarmSoundSource, volumePercent: Int, fadeInMs: Long, maxDurationMs: Long) {
        if (running) stop()
        running = true
        this.source = source
        targetGain = VolumeCurve.gain(volumePercent)
        fadeMs = fadeInMs
        firstSound = true
        requestFocus()
        playCurrent()
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(fadeStep)
        release()
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    private fun playCurrent() {
        repeat(MAX_BROKEN_IN_A_ROW) {
            if (!running) return
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(ALARM_ATTRIBUTES)
                when (val ref = source.current) {
                    is SoundRef.BuiltIn -> context.resources.openRawResourceFd(SafeHeadphonePlayer.rawResFor(ref.key))
                        .use { mp.setDataSource(it) }
                    is SoundRef.Custom -> mp.setDataSource(context, Uri.parse(ref.uri))
                    is SoundRef.Zengo -> throw IllegalArgumentException("playlist must be expanded first")
                }
                mp.isLooping = source.loops
                mp.setVolume(0f, 0f)
                mp.setOnCompletionListener { handler.post { if (running) onCompleted() } }
                mp.setOnErrorListener { _, what, extra ->
                    handler.post { if (running) onError(what, extra) }
                    true
                }
                mp.prepare()
                mp.start()
                player = mp
                gain = 0f
                if (!firstSound) fadeMs = NEXT_SONG_FADE_MS
                firstSound = false
                handler.post(fadeStep)
                listener(
                    AudioPathState.Audible(
                        deviceName = "",
                        deviceKind = OutputKind.BUILTIN_SPEAKER,
                        systemVolumeZero = audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0,
                        usedFallbackSound = source.usedFallback,
                        nowPlaying = source.currentTitle,
                        speaker = true,
                    ),
                )
                return
            } catch (e: Exception) {
                log("speaker player setup failed: ${e.javaClass.simpleName}")
                runCatching { mp.release() }
                if (!handle(source.failed())) return
            }
        }
        listener(AudioPathState.Silent(SilentReason.SOUND_FAILED))
    }

    private fun onCompleted() {
        release()
        if (handle(source.advance())) playCurrent()
    }

    private fun onError(what: Int, extra: Int) {
        log("speaker player error $what/$extra")
        release()
        if (handle(source.failed())) playCurrent()
    }

    /** Returns true when there is something more to play. */
    private fun handle(step: SourceStep): Boolean = when (step) {
        SourceStep.Continue -> true
        is SourceStep.Ended -> {
            if (step.vibrateOnly && !step.stopAlarm && source.current is SoundRef.BuiltIn) {
                listener(AudioPathState.Silent(SilentReason.SOUND_FAILED))
            } else {
                listener(AudioPathState.SourceEnded(step.stopAlarm))
            }
            false
        }
    }

    private fun release() {
        handler.removeCallbacks(fadeStep)
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ALARM_ATTRIBUTES)
            .build()
        focusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private companion object {
        const val FADE_TICK_MS = 50L
        const val NEXT_SONG_FADE_MS = 300L
        const val MAX_BROKEN_IN_A_ROW = 64

        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
