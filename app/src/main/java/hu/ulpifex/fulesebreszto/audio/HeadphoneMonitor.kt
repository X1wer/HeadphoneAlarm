package hu.ulpifex.fulesebreszto.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import hu.ulpifex.fulesebreszto.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connected outputs and their evaluation for the UI. */
data class OutputOverview(
    val connected: List<OutputDevice> = emptyList(),
    val allowedKeys: Set<String> = emptySet(),
    val status: HeadphoneStatus = HeadphoneStatus(HeadphoneStatusKind.NONE),
    val mediaVolumePercent: Int = 100,
    val strategy: List<OutputDevice>? = null,
    val lastChangeAt: Long = 0,
    val monitoring: Boolean = false,
)

/**
 * Event-driven (AudioDeviceCallback) view of the connected outputs for the screens. The
 * ringing service does its own, stricter, per-player verification; this is only for display
 * and for remembering Bluetooth/USB devices the user may allow.
 */
class HeadphoneMonitor(
    context: Context,
    private val devices: DeviceRepository,
    private val scope: CoroutineScope,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val connectedFlow: Flow<List<OutputDevice>> = callbackFlow {
        fun emitNow() {
            trySend(AudioDevices.connectedOutputs(audioManager).map { it.first })
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = emitNow()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = emitNow()
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        emitNow()
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.onEach { list -> scope.launch { devices.remember(list, System.currentTimeMillis()) } }

    val overview: StateFlow<OutputOverview> = combine(connectedFlow, devices.allowedKeys) { connected, keys ->
        val volume = AudioDevices.mediaVolumePercent(audioManager)
        OutputOverview(
            connected = connected,
            allowedKeys = keys,
            status = HeadphoneStatusResolver.resolve(connected, OutputPolicy(keys), volume),
            mediaVolumePercent = volume,
            strategy = AudioDevices.mediaStrategyDevices(audioManager),
            lastChangeAt = System.currentTimeMillis(),
            monitoring = true,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), OutputOverview())

    /** Re-reads volume etc. without a device change (e.g. when a screen resumes). */
    fun mediaVolumePercent(): Int = AudioDevices.mediaVolumePercent(audioManager)
}
