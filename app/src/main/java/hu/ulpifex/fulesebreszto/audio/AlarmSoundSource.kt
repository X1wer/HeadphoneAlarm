package hu.ulpifex.fulesebreszto.audio

import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.NextSong
import hu.ulpifex.fulesebreszto.domain.PlaylistPlan
import hu.ulpifex.fulesebreszto.domain.PlaylistSequence
import hu.ulpifex.fulesebreszto.domain.ResolvedEnd
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.integration.ZengoTrack
import kotlin.random.Random

/** What the player should do after a sound finished or failed. */
sealed interface SourceStep {
    /** [AlarmSoundSource.current] now points to the next sound: play it. */
    data object Continue : SourceStep

    /** Nothing more to play. [stopAlarm]: end the alarm; [vibrateOnly]: keep it ringing silently. */
    data class Ended(val vibrateOnly: Boolean, val stopAlarm: Boolean) : SourceStep
}

/** The sounds an alarm (or a test playback) plays, one after another. */
interface AlarmSoundSource {
    val current: SoundRef

    /** Song shown while it plays (playlist alarms), or null. */
    val currentTitle: String?

    /** A single sound loops natively; playlist songs play once and then [advance] is called. */
    val loops: Boolean

    /** True once a missing or broken file made the source switch to the built-in tone. */
    val usedFallback: Boolean

    fun advance(): SourceStep

    fun failed(): SourceStep
}

/** One built-in tone or sound file; a broken file falls back to the built-in tone. */
class SingleSoundSource(
    sound: SoundRef,
    override val loops: Boolean = true,
    fallback: Boolean = false,
) : AlarmSoundSource {
    override var current: SoundRef = sound
        private set
    override var usedFallback: Boolean = fallback
        private set
    override val currentTitle: String? = null

    override fun advance(): SourceStep = if (loops) SourceStep.Continue else SourceStep.Ended(vibrateOnly = false, stopAlarm = false)

    override fun failed(): SourceStep {
        if (current is SoundRef.BuiltIn) return SourceStep.Ended(vibrateOnly = true, stopAlarm = false)
        current = BuiltInSounds.FALLBACK.ref
        usedFallback = true
        return SourceStep.Continue
    }
}

/** The songs of a Zengő playlist, in the order of [PlaylistSequence]. */
class PlaylistSoundSource(
    private val tracks: List<ZengoTrack>,
    plan: PlaylistPlan,
    startTrackId: Long?,
    random: Random = Random.Default,
) : AlarmSoundSource {
    private val sequence = PlaylistSequence(tracks.map { it.id }, plan, startTrackId, random)
    private var builtInTone = false

    override var usedFallback: Boolean = false
        private set

    override val current: SoundRef
        get() = if (builtInTone) BuiltInSounds.FALLBACK.ref else SoundRef.Custom(tracks[sequence.current].audioUri)

    override val currentTitle: String?
        get() = if (builtInTone) null else tracks[sequence.current].let { t ->
            if (t.artist.isNullOrBlank()) t.title else "${t.title} – ${t.artist}"
        }

    override val loops: Boolean get() = builtInTone

    override fun advance(): SourceStep = if (builtInTone) SourceStep.Continue else step(sequence.next())

    override fun failed(): SourceStep {
        if (builtInTone) return SourceStep.Ended(vibrateOnly = true, stopAlarm = false)
        return when (val next = sequence.markBroken()) {
            is NextSong.Play -> SourceStep.Continue
            is NextSong.End -> {
                if (next.end == ResolvedEnd.BUILTIN_TONE) usedFallback = true
                step(next)
            }
        }
    }

    private fun step(next: NextSong): SourceStep = when (next) {
        is NextSong.Play -> SourceStep.Continue
        is NextSong.End -> when (next.end) {
            ResolvedEnd.BUILTIN_TONE -> {
                builtInTone = true
                SourceStep.Continue
            }
            ResolvedEnd.STOP_ALARM -> SourceStep.Ended(vibrateOnly = false, stopAlarm = true)
            else -> SourceStep.Ended(vibrateOnly = true, stopAlarm = false)
        }
    }
}

/** Anything that can play an [AlarmSoundSource] for a ringing alarm. */
interface AlarmAudio {
    val isRunning: Boolean
    fun start(source: AlarmSoundSource, volumePercent: Int, fadeInMs: Long, maxDurationMs: Long = 0)
    fun stop()
}
