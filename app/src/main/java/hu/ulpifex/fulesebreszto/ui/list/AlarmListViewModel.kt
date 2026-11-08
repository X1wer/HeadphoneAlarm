package hu.ulpifex.fulesebreszto.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.alarm.AlarmRingService
import hu.ulpifex.fulesebreszto.alarm.RingSessionState
import hu.ulpifex.fulesebreszto.audio.HeadphoneStatus
import hu.ulpifex.fulesebreszto.audio.HeadphoneStatusKind
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.HuFormat
import hu.ulpifex.fulesebreszto.domain.UpcomingAlarms
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

data class AlarmListUiState(
    val loaded: Boolean = false,
    val alarms: List<Alarm> = emptyList(),
    val use24h: Boolean = true,
    val nextTitle: String = "Nincs bekapcsolt ébresztés.",
    val nextCountdown: String? = null,
    val headphones: HeadphoneStatus = HeadphoneStatus(HeadphoneStatusKind.NONE),
)

class AlarmListViewModel(private val c: AppContainer) : ViewModel() {

    private val minuteTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(20_000)
        }
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val state: StateFlow<AlarmListUiState> = combine(
        c.alarms.alarms, c.settings.settings, c.headphones.overview, minuteTicker,
    ) { alarms, settings, overview, _ ->
        val now = ZonedDateTime.now(c.clock)
        val next = UpcomingAlarms.nextRing(alarms, now)
        AlarmListUiState(
            loaded = true,
            alarms = UpcomingAlarms.sortedForList(alarms),
            use24h = settings.use24h,
            nextTitle = next?.let {
                val at = Instant.ofEpochMilli(it.at).atZone(now.zone)
                val prefix = if (it.isSnooze) "Következő ébresztés (szundi): " else "Következő ébresztés: "
                prefix + HuFormat.relativeDayTime(at, now, settings.use24h)
            } ?: "Nincs bekapcsolt ébresztés.",
            nextCountdown = next?.let { HuFormat.countdown(now.toInstant().toEpochMilli(), it.at) },
            headphones = overview.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        val next = c.engine.setEnabled(alarm.id, enabled)
        if (enabled && next != null) {
            _messages.emit("Az ébresztés ${HuFormat.countdown(System.currentTimeMillis(), next)} szól")
        }
    }

    fun duplicate(alarm: Alarm) = viewModelScope.launch {
        c.engine.duplicate(alarm.id)
        _messages.emit("Másolat létrehozva (kikapcsolva)")
    }

    fun delete(context: Context, alarm: Alarm) = viewModelScope.launch {
        if (RingSessionState.isRinging && RingSessionState.state.value?.alarmIds?.contains(alarm.id) == true) {
            AlarmRingService.sendAction(context, AlarmRingService.ACTION_DISMISS)
        }
        c.engine.delete(alarm.id)
        _messages.emit("Ébresztés törölve")
    }
}
