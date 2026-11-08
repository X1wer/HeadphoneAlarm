package hu.ulpifex.fulesebreszto.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.alarm.AlarmRingService
import hu.ulpifex.fulesebreszto.alarm.RingSessionState
import hu.ulpifex.fulesebreszto.audio.HeadphoneStatus
import hu.ulpifex.fulesebreszto.audio.HeadphoneStatusKind
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.UpcomingAlarms
import hu.ulpifex.fulesebreszto.domain.UpcomingRing
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class AlarmListUiState(
    val loaded: Boolean = false,
    val alarms: List<Alarm> = emptyList(),
    val use24h: Boolean = true,
    val next: UpcomingRing? = null,
    val now: ZonedDateTime = ZonedDateTime.now(),
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
        AlarmListUiState(
            loaded = true,
            alarms = UpcomingAlarms.sortedForList(alarms),
            use24h = settings.use24h,
            next = UpcomingAlarms.nextRing(alarms, now),
            now = now,
            headphones = overview.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    private fun text(id: Int, vararg args: Any) = c.localized().getString(id, *args)

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        val next = c.engine.setEnabled(alarm.id, enabled)
        if (enabled && next != null) {
            val countdown = AppLocale.timeFormat(c.appContext).countdown(System.currentTimeMillis(), next)
            _messages.emit(text(R.string.msg_rings_in, countdown))
        }
    }

    fun duplicate(alarm: Alarm) = viewModelScope.launch {
        c.engine.duplicate(alarm.id)
        _messages.emit(text(R.string.msg_copy_created))
    }

    fun delete(context: Context, alarm: Alarm) = viewModelScope.launch {
        if (RingSessionState.isRinging && RingSessionState.state.value?.alarmIds?.contains(alarm.id) == true) {
            AlarmRingService.sendAction(context, AlarmRingService.ACTION_DISMISS)
        }
        c.engine.delete(alarm.id)
        _messages.emit(text(R.string.msg_deleted))
    }
}
