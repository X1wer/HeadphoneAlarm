package hu.ulpifex.fulesebreszto.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.data.AppSettings
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.NextTriggerCalculator
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.WeekDays
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hu.ulpifex.fulesebreszto.domain.AlarmOutput
import hu.ulpifex.fulesebreszto.domain.PlaylistEnd
import hu.ulpifex.fulesebreszto.domain.PlaylistOrder
import hu.ulpifex.fulesebreszto.integration.ZengoTrack
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class EditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val draft: Alarm = Alarm(),
    val use24h: Boolean = true,
    @StringRes val error: Int? = null,
    val saving: Boolean = false,
)

sealed interface EditEvent {
    data class Saved(val message: String) : EditEvent
    data object Deleted : EditEvent
}

class AlarmEditViewModel(private val c: AppContainer, private val alarmId: Long) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EditEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<EditEvent> = _events

    init {
        viewModelScope.launch {
            val settings = c.settings.current()
            val existing = if (alarmId != 0L) c.alarms.get(alarmId) else null
            _state.value = EditUiState(
                loaded = true,
                isNew = existing == null,
                draft = existing ?: newAlarm(settings),
                use24h = settings.use24h,
            )
        }
    }

    private fun newAlarm(s: AppSettings) = Alarm(
        label = c.localized().getString(R.string.default_alarm_label),
        hour = 6,
        minute = 30,
        days = WeekDays.WORKDAYS,
        enabled = true,
        volumePercent = s.defaultVolume,
        snoozeMinutes = s.defaultSnoozeMinutes,
        maxRingMinutes = s.defaultMaxRingMinutes,
        vibrateWithHeadphones = s.defaultVibrateWithHeadphones,
    )

    private fun edit(transform: (Alarm) -> Alarm) = _state.update { it.copy(draft = transform(it.draft), error = null) }

    fun setTime(hour: Int, minute: Int) = edit { it.copy(hour = hour, minute = minute) }
    fun setLabel(label: String) = edit { it.copy(label = label.take(60)) }
    fun setVolume(percent: Int) = edit { it.copy(volumePercent = percent.coerceIn(0, 100)) }
    fun setVibrateWithHeadphones(value: Boolean) = edit { it.copy(vibrateWithHeadphones = value) }
    fun setSnooze(minutes: Int) = edit { it.copy(snoozeMinutes = minutes.coerceIn(Alarm.MIN_SNOOZE, Alarm.MAX_SNOOZE)) }
    fun setMaxRing(minutes: Int) = edit { it.copy(maxRingMinutes = minutes.coerceIn(Alarm.MIN_RING, Alarm.MAX_RING)) }
    fun setSound(ref: String, name: String) = edit {
        val sound = SoundRef.parse(ref)
        // Another playlist: its first-song choice no longer applies.
        val playlist = if (sound == it.sound) it.playlist else it.playlist.copy(startTrackId = null, startTrackTitle = null)
        it.copy(sound = sound, soundName = name, playlist = playlist)
    }
    fun setOutput(output: AlarmOutput) = edit { it.copy(output = output) }
    fun setColor(color: Int?) = edit { it.copy(color = color) }
    fun setPlaylistOrder(order: PlaylistOrder) = edit { it.copy(playlist = it.playlist.copy(order = order)) }
    fun setPlaylistEnd(end: PlaylistEnd) = edit { it.copy(playlist = it.playlist.copy(end = end)) }
    fun setStartTrack(id: Long?, title: String?) =
        edit { it.copy(playlist = it.playlist.copy(startTrackId = id, startTrackTitle = title)) }

    /** Songs of the selected Zengő playlist (start-song picker), or null if Zengő cannot be read. */
    suspend fun playlistTracks(): List<ZengoTrack>? {
        val sound = _state.value.draft.sound as? SoundRef.Zengo ?: return null
        return withContext(Dispatchers.IO) { c.zengo.tracks(sound.authority, sound.playlistId) }
    }
    fun setDate(date: LocalDate) = edit { it.copy(oneShotDate = date) }
    fun setDays(days: WeekDays) = edit { withDays(it, days) }
    fun toggleDay(day: DayOfWeek) = edit { withDays(it, it.days.toggle(day)) }

    private fun withDays(alarm: Alarm, days: WeekDays): Alarm {
        val date = if (days.isEmpty) {
            alarm.oneShotDate ?: NextTriggerCalculator.defaultOneShotDate(alarm.hour, alarm.minute, ZonedDateTime.now(c.clock))
        } else {
            alarm.oneShotDate
        }
        return alarm.copy(days = days, oneShotDate = date)
    }

    /** When the draft would ring next, with the "now" it was computed for (preview line). */
    fun previewNext(draft: Alarm): Pair<ZonedDateTime, ZonedDateTime>? {
        val now = ZonedDateTime.now(c.clock)
        val next = NextTriggerCalculator.next(draft.copy(lastTriggeredAt = null), now) ?: return null
        return next to now
    }

    fun save() {
        val draft = _state.value.draft
        if (_state.value.saving) return
        if (draft.days.isEmpty) {
            val date = draft.oneShotDate ?: LocalDate.now(c.clock)
            val at = ZonedDateTime.of(date, LocalTime.of(draft.hour, draft.minute), c.clock.zone)
            if (!at.toInstant().isAfter(c.clock.instant())) {
                _state.update { it.copy(error = R.string.error_past) }
                return
            }
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val (_, next) = c.engine.save(draft.copy(enabled = true))
            val res = c.localized()
            val message = next?.let {
                res.getString(R.string.msg_rings_in, AppLocale.timeFormat(res).countdown(System.currentTimeMillis(), it))
            } ?: res.getString(R.string.msg_saved)
            _events.emit(EditEvent.Saved(message))
        }
    }

    fun delete() {
        if (_state.value.isNew) return
        viewModelScope.launch {
            c.engine.delete(alarmId)
            _events.emit(EditEvent.Deleted)
        }
    }
}
