package hu.ulpifex.fulesebreszto.ui.sounds

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.data.CustomSound
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.SoundRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundPickerViewModel(private val app: Application, private val c: AppContainer) : ViewModel() {

    val customSounds: StateFlow<List<CustomSound>> =
        c.sounds.sounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preview = SoundPreview(app, c)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun play(ref: SoundRef) = viewModelScope.launch {
        val volume = c.settings.current().defaultVolume
        preview.play(ref, volume)
    }

    fun stopPreview() = preview.stop()

    /** Stores a document picked with ACTION_OPEN_DOCUMENT, keeping a persistable read grant. */
    fun addCustom(uri: Uri) = viewModelScope.launch {
        val resolver = app.contentResolver
        val granted = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        val playable = withContext(Dispatchers.IO) { isPlayableAudio(uri) }
        if (!granted || !playable) {
            runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            _messages.emit(if (!granted) "A fájlhoz nem kapható tartós hozzáférés." else "A fájl nem játszható le ezen a készüléken.")
            return@launch
        }
        val name = withContext(Dispatchers.IO) { displayName(uri) }
        c.sounds.add(uri.toString(), name, System.currentTimeMillis())
        _messages.emit("Hozzáadva: $name")
    }

    fun removeCustom(sound: CustomSound) = viewModelScope.launch {
        val fallback = BuiltInSounds.DEFAULT
        val replaced = c.alarms.replaceSound(sound.uri, fallback.ref.serialized, fallback.displayName)
        c.sounds.remove(sound.uri)
        runCatching {
            app.contentResolver.releasePersistableUriPermission(Uri.parse(sound.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _messages.emit(
            if (replaced > 0) "Eltávolítva. $replaced ébresztés a(z) „${fallback.displayName}” hangra váltott."
            else "Eltávolítva: ${sound.displayName}",
        )
    }

    private fun isPlayableAudio(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" ||
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0
        } catch (e: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun displayName(uri: Uri): String {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it.substringBeforeLast('.') }
            }
        }
        return uri.lastPathSegment ?: "Saját hang"
    }

    override fun onCleared() {
        preview.stop()
    }
}
