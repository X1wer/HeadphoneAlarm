package hu.ulpifex.fulesebreszto.alarm

import hu.ulpifex.fulesebreszto.audio.AlarmSoundSource
import hu.ulpifex.fulesebreszto.audio.PlaylistSoundSource
import hu.ulpifex.fulesebreszto.audio.SingleSoundSource
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.PlaylistPlanner
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.TriggerKind
import hu.ulpifex.fulesebreszto.integration.ZengoApp
import hu.ulpifex.fulesebreszto.integration.ZengoClient
import hu.ulpifex.fulesebreszto.integration.ZengoRequest

/**
 * What a ringing alarm plays. [local] is what this app plays itself; for a Zengő playlist alarm
 * [zengo] asks Zengő to play the list in its own player, and [local] (the same list) is the fallback
 * when Zengő cannot.
 */
data class RingSound(val local: AlarmSoundSource, val zengo: ZengoRequest? = null)

object AlarmSounds {
    /**
     * A Zengő playlist is read now (fresh songs, Zengő's current shuffle/repeat mode); when Zengő is
     * missing, the permission is gone, the list is empty or the phone has not been unlocked since
     * boot, the built-in tone plays instead. Call off the main thread.
     */
    fun soundFor(alarm: Alarm, zengo: ZengoClient, kind: TriggerKind, log: (String) -> Unit): RingSound {
        val sound = alarm.sound
        if (sound !is SoundRef.Zengo) return RingSound(SingleSoundSource(sound))
        var tracks = zengo.tracks(sound.authority, sound.playlistId)
        if (tracks == null) {
            // Zengő is usually not running at alarm time; give a cold start one more chance.
            log("zengo playlist not readable yet, retrying")
            Thread.sleep(RETRY_DELAY_MS)
            tracks = zengo.tracks(sound.authority, sound.playlistId)
        }
        if (tracks.isNullOrEmpty()) {
            log("zengo playlist unavailable (${if (tracks == null) "no access" else "empty"}), using built-in tone")
            return RingSound(SingleSoundSource(BuiltInSounds.FALLBACK.ref, fallback = true))
        }
        val plan = PlaylistPlanner.resolve(alarm.playlist, zengo.mode(sound.authority))
        log("zengo playlist with ${tracks.size} song(s), shuffle=${plan.shuffle}, end=${plan.end}")
        val local = PlaylistSoundSource(tracks, plan, alarm.playlist.startTrackId)
        val app = ZengoApp.forAuthority(sound.authority) ?: return RingSound(local)
        val request = ZengoRequest(
            app = app,
            playlistId = sound.playlistId,
            startTrackId = alarm.playlist.startTrackId,
            plan = plan,
            key = "alarm-${alarm.id}",
            // After a snooze the music continues where it was paused.
            preferResume = kind == TriggerKind.SNOOZE,
        )
        return RingSound(local, request)
    }

    private const val RETRY_DELAY_MS = 700L
}
