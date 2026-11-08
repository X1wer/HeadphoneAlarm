package hu.ulpifex.fulesebreszto.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.audio.OutputOverview
import hu.ulpifex.fulesebreszto.audio.VibrationController
import hu.ulpifex.fulesebreszto.data.AppSettings
import hu.ulpifex.fulesebreszto.data.KnownDevice
import hu.ulpifex.fulesebreszto.data.ThemeMode
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.HuFormat
import hu.ulpifex.fulesebreszto.ui.sounds.SoundPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class SettingsViewModel(private val app: Application, private val c: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val overview: StateFlow<OutputOverview> = c.headphones.overview
    val knownDevices: StateFlow<List<KnownDevice>> =
        c.devices.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preview = SoundPreview(app, c)
    private val vibration = VibrationController(app)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setThemeMode(mode) }
    fun setUse24h(value: Boolean) = viewModelScope.launch { c.settings.setUse24h(value) }
    fun setDefaultVolume(value: Int) = viewModelScope.launch { c.settings.setDefaultVolume(value) }
    fun setDefaultSnooze(value: Int) = viewModelScope.launch { c.settings.setDefaultSnooze(value) }
    fun setDefaultMaxRing(value: Int) = viewModelScope.launch { c.settings.setDefaultMaxRing(value) }
    fun setDefaultVibrate(value: Boolean) = viewModelScope.launch { c.settings.setDefaultVibrate(value) }

    fun setDeviceAllowed(device: KnownDevice, allowed: Boolean) = viewModelScope.launch {
        c.devices.setAllowed(device.key, allowed)
        c.log.log("device ${device.kind} allowed=$allowed")
    }

    fun forgetDevice(device: KnownDevice) = viewModelScope.launch { c.devices.forget(device.key) }

    fun testHeadphones() = preview.play(BuiltInSounds.BEEP.ref, settings.value.defaultVolume)
    fun stopHeadphoneTest() = preview.stop()

    fun testVibration() {
        if (!vibration.hasVibrator) {
            viewModelScope.launch { _messages.emit("Ezen a készüléken nincs rezgőmotor.") }
            return
        }
        vibration.testPulse()
    }

    fun testAlarm(minutes: Int) = viewModelScope.launch {
        val at = c.engine.scheduleTest(minutes)
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        _messages.emit("Próbaébresztés ${HuFormat.time(time.hour, time.minute)}-kor ($minutes perc múlva)")
    }

    fun diagnostics(): List<String> = c.log.read()
    fun clearDiagnostics() = c.log.clear()

    override fun onCleared() {
        preview.stop()
    }
}
