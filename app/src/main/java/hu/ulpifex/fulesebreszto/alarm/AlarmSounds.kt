package hu.ulpifex.fulesebreszto.alarm

import hu.ulpifex.fulesebreszto.audio.AlarmSoundSource
import hu.ulpifex.fulesebreszto.audio.PlaylistSoundSource
import hu.ulpifex.fulesebreszto.audio.SingleSoundSource
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.PlaylistPlanner
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.integration.ZengoClient

object AlarmSounds {
    /**
     * What a ringing alarm plays. A Zengő playlist is read now (fresh songs, Zengő's current
     * shuffle/repeat mode); when Zengő is missing, the permission is gone, the list is empty or the
     * phone has not been unlocked since boot, the built-in tone plays instead.
     * Call off the main thread.
     */
    fun sourceFor(alarm: Alarm, zengo: ZengoClient, log: (String) -> Unit): AlarmSoundSource {
        val sound = alarm.sound
        if (sound !is SoundRef.Zengo) return SingleSoundSource(sound)
        var tracks = zengo.tracks(sound.authority, sound.playlistId)
        if (tracks == null) {
            // Zengő is usually not running at alarm time; give a cold start one more chance.
            log("zengo playlist not readable yet, retrying")
            Thread.sleep(RETRY_DELAY_MS)
            tracks = zengo.tracks(sound.authority, sound.playlistId)
        }
        if (tracks.isNullOrEmpty()) {
            log("zengo playlist unavailable (${if (tracks == null) "no access" else "empty"}), using built-in tone")
            return SingleSoundSource(BuiltInSounds.FALLBACK.ref, fallback = true)
        }
        val plan = PlaylistPlanner.resolve(alarm.playlist, zengo.mode(sound.authority))
        log("zengo playlist with ${tracks.size} song(s), shuffle=${plan.shuffle}, end=${plan.end}")
        return PlaylistSoundSource(tracks, plan, alarm.playlist.startTrackId)
    }

    private const val RETRY_DELAY_MS = 700L
}
