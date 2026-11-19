package hu.ulpifex.fulesebreszto.audio

import hu.ulpifex.fulesebreszto.domain.AlarmStateMachine
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.PlaylistPlan
import hu.ulpifex.fulesebreszto.domain.ResolvedEnd
import hu.ulpifex.fulesebreszto.domain.RingEffect
import hu.ulpifex.fulesebreszto.domain.RingEvent
import hu.ulpifex.fulesebreszto.domain.RingState
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.integration.ZengoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSoundSourceTest {

    private val tracks = listOf(
        ZengoTrack(1, "Hajnal", "Zenekar", 1000, "content://z/audio/1"),
        ZengoTrack(2, "Reggel", null, 1000, "content://z/audio/2"),
    )

    private fun plan(end: ResolvedEnd) = PlaylistPlan(shuffle = false, repeatOne = false, stopAfterFirst = false, end = end)

    @Test
    fun `single sound falls back to the built-in tone once`() {
        val source = SingleSoundSource(SoundRef.Custom("content://gone"))
        assertTrue(source.loops)
        assertEquals(SourceStep.Continue, source.failed())
        assertEquals(BuiltInSounds.FALLBACK.ref, source.current)
        assertTrue(source.usedFallback)
        // The built-in tone itself failing leaves only vibration.
        assertEquals(SourceStep.Ended(vibrateOnly = true, stopAlarm = false), source.failed())
    }

    @Test
    fun `non-looping test sound simply ends`() {
        assertEquals(SourceStep.Ended(vibrateOnly = false, stopAlarm = false), SingleSoundSource(BuiltInSounds.BEEP.ref, loops = false).advance())
    }

    @Test
    fun `playlist plays its songs with titles, then follows the end action`() {
        val source = PlaylistSoundSource(tracks, plan(ResolvedEnd.VIBRATE_ONLY), startTrackId = null)
        assertFalse(source.loops)
        assertEquals(SoundRef.Custom("content://z/audio/1"), source.current)
        assertEquals("Hajnal – Zenekar", source.currentTitle)
        assertEquals(SourceStep.Continue, source.advance())
        assertEquals("Reggel", source.currentTitle)
        assertEquals(SourceStep.Ended(vibrateOnly = true, stopAlarm = false), source.advance())

        val stopping = PlaylistSoundSource(tracks, plan(ResolvedEnd.STOP_ALARM), startTrackId = 2)
        assertEquals(SourceStep.Ended(vibrateOnly = false, stopAlarm = true), stopping.advance())
    }

    @Test
    fun `playlist can switch to the built-in tone at the end or when every song is broken`() {
        val source = PlaylistSoundSource(tracks, plan(ResolvedEnd.BUILTIN_TONE), startTrackId = 2)
        assertEquals(SourceStep.Continue, source.advance())
        assertEquals(BuiltInSounds.FALLBACK.ref, source.current)
        assertTrue(source.loops)
        assertNull(source.currentTitle)

        val broken = PlaylistSoundSource(tracks, plan(ResolvedEnd.RESTART_LIST), startTrackId = null)
        assertEquals(SourceStep.Continue, broken.failed()) // song 1 broken → song 2
        assertEquals("Reggel", broken.currentTitle)
        assertEquals(SourceStep.Continue, broken.failed()) // song 2 broken too → built-in tone
        assertEquals(BuiltInSounds.FALLBACK.ref, broken.current)
        assertTrue(broken.usedFallback)
    }

    @Test
    fun `loud alarm playing on the speaker has its own state and stops cleanly`() {
        val machine = AlarmStateMachine(vibrateWithHeadphones = false)
        machine.handle(RingEvent.Trigger)
        val effects = machine.handle(RingEvent.AudioVerified("", speaker = true))
        assertEquals(RingState.PLAYING_SPEAKER, machine.state)
        assertTrue(RingEffect.StopVibration in effects)
        assertTrue(machine.state.isRinging)
        assertTrue(RingEffect.StopAudio in machine.handle(RingEvent.Dismiss))
        assertEquals(RingState.DISMISSED, machine.state)
    }
}
