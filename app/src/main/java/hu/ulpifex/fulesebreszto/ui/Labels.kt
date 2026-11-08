package hu.ulpifex.fulesebreszto.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.alarm.RingUiState
import hu.ulpifex.fulesebreszto.audio.OutputDevice
import hu.ulpifex.fulesebreszto.audio.OutputKind
import hu.ulpifex.fulesebreszto.audio.UnverifiedReason
import hu.ulpifex.fulesebreszto.data.ThemeMode
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.RingState
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.TextLang
import hu.ulpifex.fulesebreszto.domain.TimeFormat
import hu.ulpifex.fulesebreszto.i18n.AppLanguage

/** Translations for enums that the pure domain/audio layers expose without any text. */

@StringRes
fun OutputKind.labelRes(): Int = when (this) {
    OutputKind.WIRED_HEADPHONES -> R.string.kind_wired_headphones
    OutputKind.WIRED_HEADSET -> R.string.kind_wired_headset
    OutputKind.USB_HEADSET -> R.string.kind_usb_headset
    OutputKind.USB_DEVICE -> R.string.kind_usb_device
    OutputKind.BLUETOOTH_A2DP -> R.string.kind_bt_a2dp
    OutputKind.BLE_HEADSET -> R.string.kind_ble_headset
    OutputKind.HEARING_AID -> R.string.kind_hearing_aid
    OutputKind.BLE_SPEAKER -> R.string.kind_ble_speaker
    OutputKind.BUILTIN_SPEAKER -> R.string.kind_speaker
    OutputKind.BUILTIN_EARPIECE -> R.string.kind_earpiece
    OutputKind.OTHER -> R.string.kind_other
}

@StringRes
fun UnverifiedReason.textRes(): Int = when (this) {
    UnverifiedReason.NO_ALLOWED_DEVICE -> R.string.unverified_no_allowed
    UnverifiedReason.TARGET_DISCONNECTED -> R.string.unverified_disconnected
    UnverifiedReason.NO_ROUTE_INFO -> R.string.unverified_no_route
    UnverifiedReason.ROUTED_ELSEWHERE -> R.string.unverified_elsewhere
    UnverifiedReason.UNSAFE_OUTPUT_ACTIVE -> R.string.unverified_unsafe
    UnverifiedReason.CALL_OR_OTHER_MODE -> R.string.unverified_call
    UnverifiedReason.FOCUS_LOST -> R.string.unverified_focus
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.SYSTEM -> R.string.theme_system
}

@StringRes
fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.HUNGARIAN -> R.string.language_hu
    AppLanguage.ENGLISH -> R.string.language_en
}

@StringRes
fun builtInSoundNameRes(key: String): Int = when (key) {
    BuiltInSounds.SOFT.key -> R.string.sound_soft
    BuiltInSounds.BEEP.key -> R.string.sound_beep
    BuiltInSounds.CHIME.key -> R.string.sound_chime
    else -> R.string.sound_classic
}

/** Built-in sounds are shown in the current language; custom files by their stored name. */
fun soundName(context: Context, sound: SoundRef, storedName: String): String = when (sound) {
    is SoundRef.BuiltIn -> context.getString(builtInSoundNameRes(sound.key))
    is SoundRef.Custom -> storedName
}

@Composable
fun soundName(alarm: Alarm): String = when (val s = alarm.sound) {
    is SoundRef.BuiltIn -> stringResource(builtInSoundNameRes(s.key))
    is SoundRef.Custom -> alarm.soundName
}

@Composable
fun deviceName(device: OutputDevice): String = device.name.ifBlank { stringResource(device.kind.labelRes()) }

/** The formatter matching the language the current screen is rendered in. */
@Composable
fun rememberTimeFormat(): TimeFormat {
    val language = LocalConfiguration.current.locales[0].language
    return TimeFormat.of(if (language == "hu") TextLang.HU else TextLang.EN)
}

/** Text describing the audio path of the ringing alarm (screen and notification). */
fun ringStatusText(context: Context, ui: RingUiState): String = when {
    ui.state == RingState.TRIGGERED || ui.state == RingState.CHECKING_AUDIO -> context.getString(R.string.audio_checking)
    ui.state == RingState.PLAYING_HEADPHONES && ui.systemVolumeZero -> context.getString(R.string.audio_playing_volume_zero)
    ui.state == RingState.PLAYING_HEADPHONES ->
        ui.audioDevice?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.audio_playing_named, it) }
            ?: context.getString(R.string.audio_playing)
    else -> context.getString(
        when (ui.silentReason) {
            SilentReason.NO_HEADPHONES, null -> R.string.audio_no_headphones
            SilentReason.OUTPUT_LOST -> R.string.audio_lost
            SilentReason.ROUTE_UNVERIFIED -> R.string.audio_unverified
            SilentReason.SOUND_FAILED -> R.string.audio_failed
            SilentReason.VOLUME_ZERO -> R.string.audio_volume_zero
        },
    )
}
