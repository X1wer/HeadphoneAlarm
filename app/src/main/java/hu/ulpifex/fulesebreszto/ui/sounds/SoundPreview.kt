package hu.ulpifex.fulesebreszto.ui.sounds

import android.content.Context
import android.os.Handler
import android.os.Looper
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.alarm.RingSessionState
import hu.ulpifex.fulesebreszto.audio.AudioPathState
import hu.ulpifex.fulesebreszto.audio.SafeHeadphonePlayer
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.ui.textRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PreviewState(val playingRef: String? = null, val message: String? = null, val audible: Boolean = false)

/**
 * Test playback for the sound picker and the settings screen. Uses the same fail-closed
 * [SafeHeadphonePlayer] as real alarms, so a preview can never reach the phone speaker.
 */
class SoundPreview(private val context: Context, private val container: AppContainer) {
    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private var allowed: Set<String> = emptySet()
    private val player = SafeHeadphonePlayer(
        context = context,
        allowedKeys = { allowed },
        log = container.log::log,
        listener = ::onState,
        onAutoStop = { _state.value = _state.value.copy(playingRef = null, audible = false, message = text(R.string.preview_ended)) },
    )

    private fun text(id: Int, vararg args: Any): String = AppLocale.localized(context).getString(id, *args)

    fun play(ref: SoundRef, volumePercent: Int) {
        if (RingSessionState.isRinging) {
            _state.value = PreviewState(message = text(R.string.preview_ringing_blocked))
            return
        }
        container.appScope.launch {
            val keys = container.devices.allowedKeysNow()
            Handler(Looper.getMainLooper()).post {
                allowed = keys
                _state.value = PreviewState(playingRef = ref.serialized, message = text(R.string.audio_checking))
                player.start(ref, volumePercent.coerceAtLeast(5), fadeInMs = 300, loop = true, maxDurationMs = PREVIEW_MS)
            }
        }
    }

    fun stop() {
        player.stop()
        _state.value = _state.value.copy(playingRef = null, audible = false)
    }

    private fun onState(state: AudioPathState) {
        val current = _state.value
        _state.value = when (state) {
            AudioPathState.Checking -> current.copy(message = text(R.string.audio_checking), audible = false)
            is AudioPathState.Audible -> current.copy(
                message = text(R.string.preview_playing, state.deviceName) +
                    (if (state.systemVolumeZero) text(R.string.preview_volume_zero_suffix) else "") +
                    (if (state.usedFallbackSound) text(R.string.preview_fallback_suffix) else ""),
                audible = true,
            )
            is AudioPathState.Silent -> {
                if (!player.isRunning) {
                    current.copy(playingRef = null, audible = false)
                } else {
                    current.copy(
                        audible = false,
                        message = when (state.reason) {
                            SilentReason.NO_HEADPHONES -> text(R.string.preview_no_headphones)
                            SilentReason.OUTPUT_LOST -> text(R.string.preview_lost)
                            SilentReason.ROUTE_UNVERIFIED ->
                                text(R.string.preview_unverified, state.detail?.let { text(it.textRes()) } ?: text(R.string.unknown))
                            SilentReason.SOUND_FAILED -> text(R.string.preview_failed)
                            SilentReason.VOLUME_ZERO -> text(R.string.preview_volume_zero)
                        },
                    )
                }
            }
        }
        if (!player.isRunning && _state.value.playingRef != null) {
            _state.value = _state.value.copy(playingRef = null, audible = false)
        }
    }

    private companion object {
        const val PREVIEW_MS = 8_000L
    }
}
