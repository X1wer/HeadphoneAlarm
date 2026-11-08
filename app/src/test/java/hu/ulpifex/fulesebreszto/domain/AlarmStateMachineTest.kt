package hu.ulpifex.fulesebreszto.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStateMachineTest {

    private fun ringing(vibrateWithHeadphones: Boolean = false) =
        AlarmStateMachine(vibrateWithHeadphones).apply { handle(RingEvent.Trigger) }

    @Test
    fun `trigger vibrates immediately and starts the audio check`() {
        val m = AlarmStateMachine(vibrateWithHeadphones = false)
        val effects = m.handle(RingEvent.Trigger)
        assertEquals(RingState.TRIGGERED, m.state)
        assertTrue(RingEffect.StartVibration in effects)
        assertTrue(RingEffect.StartAudioCheck in effects)
        assertTrue(RingEffect.ArmTimeout in effects)
        assertTrue(RingEffect.ShowRingUi in effects)
    }

    @Test
    fun `verified headphones stop the vibration when not requested`() {
        val m = ringing()
        m.handle(RingEvent.AudioCheckStarted)
        assertEquals(RingState.CHECKING_AUDIO, m.state)
        val effects = m.handle(RingEvent.AudioVerified("Fülhallgató"))
        assertEquals(RingState.PLAYING_HEADPHONES, m.state)
        assertEquals(listOf(RingEffect.StopVibration), effects)
        assertEquals("Fülhallgató", m.audioDevice)
    }

    @Test
    fun `vibration continues with headphones when requested`() {
        val m = ringing(vibrateWithHeadphones = true)
        val effects = m.handle(RingEvent.AudioVerified("Fülhallgató"))
        assertTrue(effects.isEmpty()) // already vibrating, nothing to stop
        assertEquals(RingState.PLAYING_HEADPHONES, m.state)
    }

    @Test
    fun `system volume zero keeps vibrating even with headphones`() {
        val m = ringing()
        val effects = m.handle(RingEvent.AudioVerified("Fülhallgató", systemVolumeZero = true))
        assertFalse(RingEffect.StopVibration in effects)
        assertTrue(m.systemVolumeZero)
    }

    @Test
    fun `no headphones means vibration only`() {
        val m = ringing()
        m.handle(RingEvent.AudioUnavailable(SilentReason.NO_HEADPHONES))
        assertEquals(RingState.VIBRATING_ONLY, m.state)
        assertEquals(SilentReason.NO_HEADPHONES, m.silentReason)
    }

    @Test
    fun `losing the output while playing switches to vibration and back after reconnect`() {
        val m = ringing()
        m.handle(RingEvent.AudioVerified("BT"))
        val lost = m.handle(RingEvent.AudioUnavailable(SilentReason.OUTPUT_LOST))
        assertEquals(RingState.VIBRATING_ONLY, m.state)
        assertEquals(listOf(RingEffect.StartVibration), lost)
        assertEquals(SilentReason.OUTPUT_LOST, m.silentReason)

        val checking = m.handle(RingEvent.AudioCheckStarted)
        assertEquals(RingState.CHECKING_AUDIO, m.state)
        assertTrue(checking.isEmpty()) // still vibrating while re-verifying

        val back = m.handle(RingEvent.AudioVerified("BT"))
        assertEquals(RingState.PLAYING_HEADPHONES, m.state)
        assertEquals(listOf(RingEffect.StopVibration), back)
    }

    @Test
    fun `dismiss stops everything once and is idempotent`() {
        val m = ringing()
        m.handle(RingEvent.AudioVerified("BT"))
        val first = m.handle(RingEvent.Dismiss)
        assertEquals(RingState.DISMISSED, m.state)
        assertTrue(RingEffect.StopAudio in first)
        assertTrue(RingEffect.StopVibration in first)
        assertTrue(RingEffect.CancelTimeout in first)
        assertEquals(1, first.count { it == RingEffect.CompleteDismiss })
        assertTrue(RingEffect.StopService in first)

        assertTrue(m.handle(RingEvent.Dismiss).isEmpty())
        assertTrue(m.handle(RingEvent.Snooze).isEmpty())
        assertTrue(m.handle(RingEvent.Timeout).isEmpty())
    }

    @Test
    fun `late audio events after dismiss cannot restart vibration or sound`() {
        val m = ringing()
        m.handle(RingEvent.Dismiss)
        assertTrue(m.handle(RingEvent.AudioUnavailable(SilentReason.OUTPUT_LOST)).isEmpty())
        assertTrue(m.handle(RingEvent.AudioVerified("BT")).isEmpty())
        assertTrue(m.handle(RingEvent.AudioCheckStarted).isEmpty())
        assertEquals(RingState.DISMISSED, m.state)
    }

    @Test
    fun `dismissed occurrence cannot be re-triggered by the same machine`() {
        val m = ringing()
        m.handle(RingEvent.Dismiss)
        assertTrue(m.handle(RingEvent.Trigger).isEmpty())
    }

    @Test
    fun `snooze schedules exactly one snooze`() {
        val m = ringing()
        val first = m.handle(RingEvent.Snooze)
        assertEquals(RingState.SNOOZED, m.state)
        assertEquals(1, first.count { it == RingEffect.ScheduleSnooze })
        assertTrue(RingEffect.StopVibration in first)
        assertTrue(RingEffect.StopAudio in first)
        assertTrue(m.handle(RingEvent.Snooze).isEmpty())
    }

    @Test
    fun `snoozed alarm rings again on the snooze trigger`() {
        val m = ringing()
        m.handle(RingEvent.Snooze)
        val again = m.handle(RingEvent.Trigger)
        assertEquals(RingState.TRIGGERED, m.state)
        assertTrue(RingEffect.StartAudioCheck in again)
        assertTrue(RingEffect.StartVibration in again)
    }

    @Test
    fun `dismissing a snoozed alarm cancels the snooze`() {
        val m = ringing()
        m.handle(RingEvent.Snooze)
        val effects = m.handle(RingEvent.Dismiss)
        assertEquals(RingState.DISMISSED, m.state)
        assertTrue(RingEffect.CancelSnooze in effects)
    }

    @Test
    fun `max ring time ends the alarm and reports it`() {
        val m = ringing()
        m.handle(RingEvent.AudioUnavailable(SilentReason.NO_HEADPHONES))
        val effects = m.handle(RingEvent.Timeout)
        assertEquals(RingState.TIMED_OUT, m.state)
        assertTrue(RingEffect.NotifyTimedOut in effects)
        assertTrue(RingEffect.StopVibration in effects)
        assertTrue(RingEffect.StopAudio in effects)
        assertTrue(RingEffect.StopService in effects)
        assertTrue(m.handle(RingEvent.Dismiss).isEmpty())
    }

    @Test
    fun `player failure keeps the alarm alive on vibration`() {
        val m = ringing()
        m.handle(RingEvent.AudioVerified("BT"))
        val effects = m.handle(RingEvent.Failure("decoder"))
        assertEquals(RingState.ERROR, m.state)
        assertTrue(RingEffect.StopAudio in effects)
        assertTrue(RingEffect.StartVibration in effects)
        assertTrue(m.handle(RingEvent.Dismiss).contains(RingEffect.CompleteDismiss))
    }

    @Test
    fun `events before trigger are ignored`() {
        val m = AlarmStateMachine(false)
        assertTrue(m.handle(RingEvent.Dismiss).isEmpty())
        assertTrue(m.handle(RingEvent.AudioVerified("x")).isEmpty())
        m.handle(RingEvent.Schedule)
        assertEquals(RingState.SCHEDULED, m.state)
    }
}
