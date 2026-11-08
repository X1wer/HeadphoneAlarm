package hu.ulpifex.fulesebreszto.alarm

import hu.ulpifex.fulesebreszto.domain.RingState
import hu.ulpifex.fulesebreszto.domain.SilentReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FinishKind { DISMISSED, SNOOZED, TIMED_OUT }

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
    val finished: FinishKind? = null,
) {
    val extraAlarms: Int get() = (alarmIds.size - 1).coerceAtLeast(0)

    val audioStatusText: String
        get() = when {
            state == RingState.TRIGGERED || state == RingState.CHECKING_AUDIO -> "Hangkimenet ellenőrzése…"
            state == RingState.PLAYING_HEADPHONES && systemVolumeZero ->
                "Fejhallgatón szól, de a médiahangerő 0 – rezgés is"
            state == RingState.PLAYING_HEADPHONES -> "Fejhallgatón szól" + (audioDevice?.let { ": $it" } ?: "")
            else -> when (silentReason) {
                SilentReason.NO_HEADPHONES, null -> "Csak rezgés – nincs fejhallgató"
                SilentReason.OUTPUT_LOST -> "Kapcsolat megszakadt – rezgésre váltva"
                SilentReason.ROUTE_UNVERIFIED -> "A hangkimenet nem igazolható – csak rezgés"
                SilentReason.SOUND_FAILED -> "A hang nem játszható le – csak rezgés"
                SilentReason.VOLUME_ZERO -> "Ébresztési hangerő 0% – csak rezgés"
            }
        }
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
