package hu.ulpifex.fulesebreszto.domain

enum class RingState {
    IDLE,
    SCHEDULED,
    TRIGGERED,
    CHECKING_AUDIO,
    PLAYING_HEADPHONES,
    /** A loud alarm playing on the speaker (alarm stream). */
    PLAYING_SPEAKER,
    VIBRATING_ONLY,
    SNOOZED,
    DISMISSED,
    TIMED_OUT,
    ERROR;

    /** States in which sound or vibration may be running and the user can act. */
    val isRinging: Boolean
        get() = this == TRIGGERED || this == CHECKING_AUDIO || this == PLAYING_HEADPHONES || this == PLAYING_SPEAKER ||
            this == VIBRATING_ONLY || this == ERROR

    val isTerminal: Boolean get() = this == DISMISSED || this == TIMED_OUT
}

/** Why the alarm is not (or no longer) audible. Shown on the alarm screen. */
enum class SilentReason {
    NO_HEADPHONES,
    OUTPUT_LOST,
    ROUTE_UNVERIFIED,
    SOUND_FAILED,
    VOLUME_ZERO,

    /** A playlist alarm played its list to the end and was set to keep vibrating. */
    PLAYLIST_ENDED,
}

sealed interface RingEvent {
    data object Schedule : RingEvent
    data object Trigger : RingEvent
    data object AudioCheckStarted : RingEvent
    data class AudioVerified(
        val deviceName: String,
        val systemVolumeZero: Boolean = false,
        val speaker: Boolean = false,
    ) : RingEvent
    data class AudioUnavailable(val reason: SilentReason) : RingEvent
    data object Snooze : RingEvent
    data object Dismiss : RingEvent
    data object Timeout : RingEvent
    data class Failure(val message: String) : RingEvent
}

sealed interface RingEffect {
    data object StartAudioCheck : RingEffect
    data object StopAudio : RingEffect
    data object StartVibration : RingEffect
    data object StopVibration : RingEffect
    data object ShowRingUi : RingEffect
    data object ArmTimeout : RingEffect
    data object CancelTimeout : RingEffect
    data object ScheduleSnooze : RingEffect
    data object CancelSnooze : RingEffect
    data object CompleteDismiss : RingEffect
    data object NotifyTimedOut : RingEffect
    data object CloseRingUi : RingEffect
    data object StopService : RingEffect
}

/**
 * The single owner of an alarm's ringing lifecycle. Every component (player, vibrator,
 * timeout, UI, notification actions) only sends events here and executes the returned
 * effects, so sound and vibration can never be driven by two independent decision makers.
 *
 * Invalid or repeated events return no effects, which makes dismiss/snooze idempotent.
 */
class AlarmStateMachine(private val vibrateWithHeadphones: Boolean) {

    var state: RingState = RingState.IDLE
        private set
    var silentReason: SilentReason? = null
        private set
    var audioDevice: String? = null
        private set
    var systemVolumeZero: Boolean = false
        private set

    private var vibrating = false

    fun handle(event: RingEvent): List<RingEffect> {
        val effects = mutableListOf<RingEffect>()
        when (event) {
            RingEvent.Schedule -> if (state == RingState.IDLE || state.isTerminal || state == RingState.SNOOZED) {
                state = RingState.SCHEDULED
            }

            RingEvent.Trigger -> if (state == RingState.IDLE || state == RingState.SCHEDULED || state == RingState.SNOOZED) {
                state = RingState.TRIGGERED
                silentReason = null
                audioDevice = null
                // Vibrate until the headphone route is proven: never silent while checking.
                effects += startVibration()
                effects += RingEffect.ShowRingUi
                effects += RingEffect.ArmTimeout
                effects += RingEffect.StartAudioCheck
            }

            RingEvent.AudioCheckStarted -> if (state.isRinging) {
                state = RingState.CHECKING_AUDIO
                audioDevice = null
                effects += startVibration()
            }

            is RingEvent.AudioVerified -> if (state.isRinging) {
                state = if (event.speaker) RingState.PLAYING_SPEAKER else RingState.PLAYING_HEADPHONES
                silentReason = null
                audioDevice = event.deviceName
                systemVolumeZero = event.systemVolumeZero
                effects += if (vibrateWithHeadphones || event.systemVolumeZero) startVibration() else stopVibration()
            }

            is RingEvent.AudioUnavailable -> if (state.isRinging) {
                state = RingState.VIBRATING_ONLY
                silentReason = event.reason
                audioDevice = null
                effects += startVibration()
            }

            is RingEvent.Failure -> if (state.isRinging) {
                state = RingState.ERROR
                silentReason = SilentReason.SOUND_FAILED
                audioDevice = null
                effects += RingEffect.StopAudio
                effects += startVibration()
            }

            RingEvent.Snooze -> if (state.isRinging) {
                state = RingState.SNOOZED
                effects += endRinging()
                effects += RingEffect.ScheduleSnooze
                effects += RingEffect.CloseRingUi
                effects += RingEffect.StopService
            }

            RingEvent.Dismiss -> when {
                state.isRinging -> {
                    state = RingState.DISMISSED
                    effects += endRinging()
                    effects += RingEffect.CompleteDismiss
                    effects += RingEffect.CloseRingUi
                    effects += RingEffect.StopService
                }
                state == RingState.SNOOZED -> {
                    state = RingState.DISMISSED
                    effects += RingEffect.CancelSnooze
                    effects += RingEffect.CompleteDismiss
                }
            }

            RingEvent.Timeout -> if (state.isRinging) {
                state = RingState.TIMED_OUT
                effects += endRinging()
                effects += RingEffect.NotifyTimedOut
                effects += RingEffect.CompleteDismiss
                effects += RingEffect.CloseRingUi
                effects += RingEffect.StopService
            }
        }
        return effects
    }

    private fun startVibration(): List<RingEffect> {
        if (vibrating) return emptyList()
        vibrating = true
        return listOf(RingEffect.StartVibration)
    }

    private fun stopVibration(): List<RingEffect> {
        if (!vibrating) return emptyList()
        vibrating = false
        return listOf(RingEffect.StopVibration)
    }

    private fun endRinging(): List<RingEffect> {
        // StopVibration is always emitted when leaving ringing, even if we think it is
        // already off, so a vibration can never outlive the alarm.
        vibrating = false
        return listOf(RingEffect.CancelTimeout, RingEffect.StopAudio, RingEffect.StopVibration)
    }
}
