package hu.ulpifex.fulesebreszto.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Translates platform AudioDeviceInfo objects into the pure [OutputDevice] model. */
object AudioDevices {

    /** Attributes used for every audible playback in the app. USAGE_ALARM is deliberately
     * avoided: the platform duplicates the alarm stream to the speaker when headphones are on. */
    val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun kindOf(type: Int): OutputKind = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputKind.WIRED_HEADPHONES
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED_HEADSET
        AudioDeviceInfo.TYPE_USB_HEADSET -> OutputKind.USB_HEADSET
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB_DEVICE
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH_A2DP
        AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.HEARING_AID
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputKind.BUILTIN_SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.BUILTIN_EARPIECE
        else -> leAudioKind(type)
    }

    private fun leAudioKind(type: Int): OutputKind {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET) return OutputKind.BLE_HEADSET
            if (type == AudioDeviceInfo.TYPE_BLE_SPEAKER) return OutputKind.BLE_SPEAKER
        }
        return OutputKind.OTHER
    }

    fun toOutput(info: AudioDeviceInfo): OutputDevice {
        val kind = kindOf(info.type)
        val name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: kind.label
        return OutputDevice(id = info.id, kind = kind, name = name, address = info.address.orEmpty())
    }

    fun connectedOutputs(audioManager: AudioManager): List<Pair<OutputDevice, AudioDeviceInfo>> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { toOutput(it) to it }

    /** Media routing as decided by the audio policy (API 33+), or null when not available. */
    fun mediaStrategyDevices(audioManager: AudioManager): List<OutputDevice>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { audioManager.getAudioDevicesForAttributes(MEDIA_ATTRIBUTES).map(::toOutput) }.getOrNull()
        } else {
            null
        }

    fun mediaVolumePercent(audioManager: AudioManager): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }
}
