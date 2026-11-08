package hu.ulpifex.fulesebreszto.domain

import hu.ulpifex.fulesebreszto.bp
import hu.ulpifex.fulesebreszto.ms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReschedulePlannerTest {

    private val now = bp(2026, 11, 9, 5, 0) // Monday
    private val alarm = Alarm(id = 1, hour = 6, minute = 30, days = WeekDays.WORKDAYS, enabled = true, maxRingMinutes = 10)

    private fun AlarmPlan.arm(kind: TriggerKind) = ops.filterIsInstance<ScheduleOp.Arm>().singleOrNull { it.kind == kind }
    private fun AlarmPlan.cancels(kind: TriggerKind) = ops.any { it is ScheduleOp.Cancel && it.kind == kind }

    @Test
    fun `enabled alarm is armed for its next occurrence`() {
        val plan = ReschedulePlanner.plan(alarm, now)
        val arm = plan.arm(TriggerKind.MAIN)!!
        assertEquals(bp(2026, 11, 9, 6, 30).ms(), arm.fireAt)
        assertEquals(arm.fireAt, arm.occurrence)
        assertEquals(arm.fireAt, plan.updated.nextTriggerAt)
        assertTrue(plan.cancels(TriggerKind.SNOOZE))
    }

    @Test
    fun `disabled alarm is cancelled`() {
        val plan = ReschedulePlanner.plan(alarm.copy(enabled = false, nextTriggerAt = 123), now)
        assertTrue(plan.cancels(TriggerKind.MAIN))
        assertTrue(plan.cancels(TriggerKind.SNOOZE))
        assertNull(plan.updated.nextTriggerAt)
    }

    @Test
    fun `modified alarm is re-armed at the new time`() {
        val armed = ReschedulePlanner.plan(alarm, now).updated
        val edited = armed.copy(hour = 7, minute = 45, nextTriggerAt = null) // engine clears it on save
        val plan = ReschedulePlanner.plan(edited, now)
        assertEquals(bp(2026, 11, 9, 7, 45).ms(), plan.arm(TriggerKind.MAIN)!!.fireAt)
    }

    @Test
    fun `deleted alarm cancels both triggers`() {
        val ops = ReschedulePlanner.planDeleted(1)
        assertEquals(setOf(TriggerKind.MAIN, TriggerKind.SNOOZE), ops.map { it.kind }.toSet())
        assertTrue(ops.all { it is ScheduleOp.Cancel })
    }

    @Test
    fun `occurrence missed by a few minutes (reboot) rings right away`() {
        val occurrence = bp(2026, 11, 9, 6, 30).ms()
        val afterBoot = bp(2026, 11, 9, 6, 34)
        val plan = ReschedulePlanner.plan(alarm.copy(nextTriggerAt = occurrence), afterBoot)
        val arm = plan.arm(TriggerKind.MAIN)!!
        assertEquals(afterBoot.ms() + ReschedulePlanner.LATE_RING_DELAY_MS, arm.fireAt)
        assertEquals(occurrence, arm.occurrence)
        assertTrue(plan.missed.isEmpty())
    }

    @Test
    fun `occurrence missed beyond the ring window is reported and the next one armed`() {
        val occurrence = bp(2026, 11, 9, 6, 30).ms()
        val plan = ReschedulePlanner.plan(alarm.copy(nextTriggerAt = occurrence), bp(2026, 11, 9, 9, 0))
        assertEquals(1, plan.missed.size)
        assertEquals(occurrence, plan.missed.single().occurrence)
        assertEquals(bp(2026, 11, 10, 6, 30).ms(), plan.arm(TriggerKind.MAIN)!!.fireAt)
    }

    @Test
    fun `missed one-shot alarm is switched off`() {
        val oneShot = alarm.copy(days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 9), nextTriggerAt = bp(2026, 11, 9, 6, 30).ms())
        val plan = ReschedulePlanner.plan(oneShot, bp(2026, 11, 9, 12, 0))
        assertFalse(plan.updated.enabled)
        assertTrue(plan.cancels(TriggerKind.MAIN))
        assertEquals(1, plan.missed.size)
    }

    @Test
    fun `already rung occurrence is not treated as missed`() {
        val occurrence = bp(2026, 11, 9, 6, 30).ms()
        val plan = ReschedulePlanner.plan(alarm.copy(nextTriggerAt = occurrence, lastTriggeredAt = occurrence), bp(2026, 11, 9, 6, 31))
        assertTrue(plan.missed.isEmpty())
        assertEquals(bp(2026, 11, 10, 6, 30).ms(), plan.arm(TriggerKind.MAIN)!!.fireAt)
    }

    @Test
    fun `pending snooze is armed, late snooze rings now, stale snooze is dropped`() {
        val until = bp(2026, 11, 9, 6, 40).ms()
        val future = ReschedulePlanner.plan(alarm.copy(snoozeUntil = until), bp(2026, 11, 9, 6, 35))
        assertEquals(until, future.arm(TriggerKind.SNOOZE)!!.fireAt)

        val late = ReschedulePlanner.plan(alarm.copy(snoozeUntil = until), bp(2026, 11, 9, 6, 45))
        assertEquals(bp(2026, 11, 9, 6, 45).ms() + ReschedulePlanner.LATE_RING_DELAY_MS, late.arm(TriggerKind.SNOOZE)!!.fireAt)

        val stale = ReschedulePlanner.plan(alarm.copy(snoozeUntil = until), bp(2026, 11, 9, 8, 0))
        assertNull(stale.updated.snoozeUntil)
        assertTrue(stale.cancels(TriggerKind.SNOOZE))
        assertTrue(stale.missed.any { it.isSnooze })
    }

    @Test
    fun `ring window used for late detection follows max ring time`() {
        val occurrence = bp(2026, 11, 9, 6, 30).ms()
        val shortWindow = alarm.copy(nextTriggerAt = occurrence, maxRingMinutes = 3)
        val plan = ReschedulePlanner.plan(shortWindow, bp(2026, 11, 9, 6, 35))
        assertEquals(1, plan.missed.size) // 5 min late > 3 min window
    }

    @Test
    fun `trigger validation rejects deleted, disabled, edited and duplicate triggers`() {
        val occurrence = bp(2026, 11, 9, 6, 30).ms()
        val armed = alarm.copy(nextTriggerAt = occurrence)
        assertTrue(TriggerValidator.isValid(armed, TriggerKind.MAIN, occurrence))
        assertFalse(TriggerValidator.isValid(null, TriggerKind.MAIN, occurrence))
        assertFalse(TriggerValidator.isValid(armed.copy(enabled = false), TriggerKind.MAIN, occurrence))
        assertFalse(TriggerValidator.isValid(armed.copy(nextTriggerAt = occurrence + 60_000), TriggerKind.MAIN, occurrence))
        assertFalse(TriggerValidator.isValid(armed.copy(lastTriggeredAt = occurrence), TriggerKind.MAIN, occurrence))

        assertTrue(TriggerValidator.isValid(armed.copy(snoozeUntil = 99), TriggerKind.SNOOZE, 99))
        assertFalse(TriggerValidator.isValid(armed.copy(snoozeUntil = null), TriggerKind.SNOOZE, 99))
    }

    @Test
    fun `marking a trigger records it and disables one-shot alarms`() {
        val occurrence = bp(2026, 11, 9, 6, 30).ms()
        val repeating = TriggerValidator.markTriggered(alarm.copy(nextTriggerAt = occurrence), TriggerKind.MAIN, occurrence)
        assertEquals(occurrence, repeating.lastTriggeredAt)
        assertTrue(repeating.enabled)

        val oneShot = TriggerValidator.markTriggered(
            alarm.copy(days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 9), nextTriggerAt = occurrence),
            TriggerKind.MAIN, occurrence,
        )
        assertFalse(oneShot.enabled)
        assertNull(oneShot.nextTriggerAt)

        val snoozed = TriggerValidator.markTriggered(alarm.copy(snoozeUntil = 5), TriggerKind.SNOOZE, 5)
        assertNull(snoozed.snoozeUntil)
    }
}
