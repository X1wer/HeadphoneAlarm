package hu.ulpifex.fulesebreszto.ui.sounds

import android.content.Context
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.alarm.RingSessionState
import hu.ulpifex.fulesebreszto.audio.AudioPathState
import hu.ulpifex.fulesebreszto.audio.SafeHeadphonePlayer
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.SoundRef
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
        onAutoStop = { _state.value = _state.value.copy(playingRef = null, audible = false, message = "A tesztlejátszás véget ért.") },
    )

    fun play(ref: SoundRef, volumePercent: Int) {
        if (RingSessionState.isRinging) {
            _state.value = PreviewState(message = "Ébresztés szól – a tesztlejátszás most nem indítható.")
            return
        }
        container.appScope.launch {
            val keys = container.devices.allowedKeysNow()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                allowed = keys
                _state.value = PreviewState(playingRef = ref.serialized, message = "Hangkimenet ellenőrzése…")
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
            AudioPathState.Checking -> current.copy(message = "Hangkimenet ellenőrzése…", audible = false)
            is AudioPathState.Audible -> current.copy(
                message = "Szól a fejhallgatón: ${state.deviceName}" +
                    (if (state.systemVolumeZero) " – de a médiahangerő 0!" else "") +
                    (if (state.usedFallbackSound) " (a fájl nem érhető el, beépített hang)" else ""),
                audible = true,
            )
            is AudioPathState.Silent -> {
                if (!player.isRunning) {
                    current.copy(playingRef = null, audible = false)
                } else {
                    current.copy(
                        audible = false,
                        message = when (state.reason) {
                            SilentReason.NO_HEADPHONES -> "Nincs engedélyezett fejhallgató – a teszthang nem indul el."
                            SilentReason.OUTPUT_LOST -> "A fejhallgató lecsatlakozott – a lejátszás leállt."
                            SilentReason.ROUTE_UNVERIFIED ->
                                "A hangkimenet nem igazolható (${state.detail?.message ?: "ismeretlen"}) – néma marad."
                            SilentReason.SOUND_FAILED -> "A hang nem játszható le."
                            SilentReason.VOLUME_ZERO -> "A hangerő 0%."
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
