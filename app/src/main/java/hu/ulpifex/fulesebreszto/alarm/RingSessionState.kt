package hu.ulpifex.fulesebreszto.alarm

import hu.ulpifex.fulesebreszto.domain.RingState
import hu.ulpifex.fulesebreszto.domain.SilentReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** CONTINUED: the alarm was closed with a sideways swipe and the music keeps playing in Zengő. */
enum class FinishKind { DISMISSED, SNOOZED, TIMED_OUT, CONTINUED }

/** Snapshot of the ringing session, rendered by the alarm screen and the notification. */
data class RingUiState(
    val alarmIds: List<Long>,
    val label: String,
    val alarmTime: String,
    val state: RingState,
    val silentReason: SilentReason?,
    val audioDevice: String?,
    val systemVolumeZero: Boolean,
    val usedFallbackSound: Boolean,
    val snoozeMinutes: Int,
    /** Colour of the (primary) alarm, used to tint the ringing screen. */
    val colorArgb: Int = 0xFF4DD0E1.toInt(),
    val loud: Boolean = false,
    /** Song currently playing (playlist alarms). */
    val nowPlaying: String? = null,
    /** The list plays in Zengő itself (not in this app). */
    val inZengo: Boolean = false,
    /** The alarm can be closed with the music left playing in Zengő (sideways swipe). */
    val canContinue: Boolean = false,
    val finished: FinishKind? = null,
) {
    val extraAlarms: Int get() = (alarmIds.size - 1).coerceAtLeast(0)
}

/** In-process bridge between the ringing service and the alarm activity. */
object RingSessionState {
    private val _state = MutableStateFlow<RingUiState?>(null)
    val state: StateFlow<RingUiState?> = _state.asStateFlow()

    val isRinging: Boolean get() = _state.value?.let { it.finished == null && it.state.isRinging } == true

    internal fun publish(value: RingUiState?) {
        _state.value = value
    }
}
