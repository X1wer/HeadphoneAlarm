package hu.ulpifex.fulesebreszto.integration

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.SessionCommand
import hu.ulpifex.fulesebreszto.domain.PlaylistPlan
import hu.ulpifex.fulesebreszto.domain.toZengoPlayMode

/**
 * Zengő's playback-session contract for alarm apps (Zengő 1.8.0+, `integration/AlarmBridge`, API 2).
 * The commands are offered only to apps that hold Zengő's ALARM_PLAYLISTS permission.
 */
object ZengoSession {
    /** Zengő's MediaSessionService (the same class name in the release and the debug build). */
    const val SERVICE_CLASS = "io.github.x1wer.szabadzene.playback.PlaybackService"

    const val ACTION_PLAY = "io.github.x1wer.szabadzene.alarm.PLAY"
    const val ACTION_VOLUME = "io.github.x1wer.szabadzene.alarm.VOLUME"
    const val ACTION_FINISH = "io.github.x1wer.szabadzene.alarm.FINISH"

    const val EXTRA_PLAYLIST_ID = "playlist_id"
    const val EXTRA_START_TRACK_ID = "start_track_id"
    const val EXTRA_SHUFFLE = "shuffle"
    const val EXTRA_REPEAT = "repeat"
    const val EXTRA_STOP_AFTER_FIRST = "stop_after_first"
    const val EXTRA_REPEAT_LAST = "repeat_last"
    const val EXTRA_ALARM_STREAM = "alarm_stream"
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_KEY = "key"
    const val EXTRA_PREFER_RESUME = "prefer_resume"
    const val EXTRA_VOLUME = "volume"
    const val EXTRA_FADE_MS = "fade_ms"
    const val EXTRA_KEEP_PLAYING = "keep_playing"

    val play = SessionCommand(ACTION_PLAY, Bundle.EMPTY)
    val volume = SessionCommand(ACTION_VOLUME, Bundle.EMPTY)
    val finish = SessionCommand(ACTION_FINISH, Bundle.EMPTY)

    fun component(app: ZengoApp) = ComponentName(app.packageName, SERVICE_CLASS)

    fun volumeArgs(gain: Float, fadeMs: Long) = Bundle().apply {
        putFloat(EXTRA_VOLUME, gain)
        putLong(EXTRA_FADE_MS, fadeMs)
    }

    fun finishArgs(keepPlaying: Boolean) = Bundle().apply { putBoolean(EXTRA_KEEP_PLAYING, keepPlaying) }
}

/**
 * A playlist alarm to be played by Zengő itself. [key] identifies the alarm, so that after a snooze
 * ([preferResume]) Zengő continues where it was paused, as long as its queue has not changed since.
 */
data class ZengoRequest(
    val app: ZengoApp,
    val playlistId: Long,
    val startTrackId: Long?,
    val plan: PlaylistPlan,
    val key: String,
    val preferResume: Boolean,
) {
    fun playArgs(loud: Boolean, deviceId: Int, resume: Boolean): Bundle {
        val mode = plan.toZengoPlayMode()
        return Bundle().apply {
            putLong(ZengoSession.EXTRA_PLAYLIST_ID, playlistId)
            putLong(ZengoSession.EXTRA_START_TRACK_ID, startTrackId ?: -1L)
            putBoolean(ZengoSession.EXTRA_SHUFFLE, mode.shuffle)
            putString(ZengoSession.EXTRA_REPEAT, mode.repeat)
            putBoolean(ZengoSession.EXTRA_STOP_AFTER_FIRST, mode.stopAfterFirst)
            putBoolean(ZengoSession.EXTRA_REPEAT_LAST, mode.repeatLast)
            putBoolean(ZengoSession.EXTRA_ALARM_STREAM, loud)
            putInt(ZengoSession.EXTRA_DEVICE_ID, deviceId)
            putString(ZengoSession.EXTRA_KEY, key)
            putBoolean(ZengoSession.EXTRA_PREFER_RESUME, resume)
        }
    }
}
