package hu.ulpifex.fulesebreszto.alarm

import hu.ulpifex.fulesebreszto.MutableClock
import hu.ulpifex.fulesebreszto.bp
import hu.ulpifex.fulesebreszto.data.AlarmStore
import hu.ulpifex.fulesebreszto.data.AppSettings
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.MissedRing
import hu.ulpifex.fulesebreszto.domain.ReschedulePlanner
import hu.ulpifex.fulesebreszto.domain.TriggerKind
import hu.ulpifex.fulesebreszto.domain.WeekDays
import hu.ulpifex.fulesebreszto.ms
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private class FakeStore : AlarmStore {
    val rows = linkedMapOf<Long, Alarm>()
    private var nextId = 1L
    override suspend fun all() = rows.values.toList()
    override suspend fun get(id: Long) = rows[id]
    override suspend fun save(alarm: Alarm): Long {
        val id = if (alarm.id == 0L) nextId++ else alarm.id
        rows[id] = alarm.copy(id = id)
        return id
    }
    override suspend fun delete(id: Long) {
        rows.remove(id)
    }
}

/** Mirrors AlarmManager semantics: one pending entry per (alarm, kind), re-arming replaces it. */
private class FakeGateway : AlarmClockGateway {
    data class Entry(val fireAt: Long, val occurrence: Long)
    val armed = linkedMapOf<Pair<Long, TriggerKind>, Entry>()
    override fun arm(alarmId: Long, kind: TriggerKind, fireAt: Long, occurrence: Long) {
        armed[alarmId to kind] = Entry(fireAt, occurrence)
    }
    override fun cancel(alarmId: Long, kind: TriggerKind) {
        armed.remove(alarmId to kind)
    }
    fun main(id: Long) = armed[id to TriggerKind.MAIN]
    fun snooze(id: Long) = armed[id to TriggerKind.SNOOZE]
}

private class FakeNotifier : AlarmNotifier {
    val missed = mutableListOf<MissedRing>()
    val snoozed = mutableMapOf<Long, Long>()
    override fun missed(missed: MissedRing) {
        this.missed += missed
    }
    override fun snoozed(alarm: Alarm, until: Long) {
        snoozed[alarm.id] = until
    }
    override fun clearSnoozed(alarmId: Long) {
        snoozed.remove(alarmId)
    }
}

class AlarmEngineTest {
    private val clock = MutableClock(bp(2026, 11, 9, 5, 0).toInstant()) // Monday 05:00
    private val store = FakeStore()
    private val gateway = FakeGateway()
    private val notifier = FakeNotifier()
    private val engine = AlarmEngine(store, gateway, notifier, clock, settings = { AppSettings() })

    private val workday0630 = Alarm(label = "Munkahelyi ébresztés", hour = 6, minute = 30, days = WeekDays.WORKDAYS, enabled = true)

    /** Simulates AlarmManager delivering the MAIN trigger that is currently armed. */
    private suspend fun fireMain(id: Long): Alarm? {
        val entry = gateway.main(id) ?: return null
        clock.now = java.time.Instant.ofEpochMilli(entry.fireAt)
        return engine.onTrigger(id, TriggerKind.MAIN, entry.occurrence)
    }

    @Test
    fun `first run creates a disabled weekday 06_30 alarm with spec defaults`() = runTest {
        engine.ensureFirstRunAlarm(isEmpty = true, defaults = AppSettings())
        val alarm = store.rows.values.single()
        assertEquals(6, alarm.hour)
        assertEquals(30, alarm.minute)
        assertEquals(WeekDays.WORKDAYS, alarm.days)
        assertEquals(60, alarm.volumePercent)
        assertEquals(10, alarm.snoozeMinutes)
        assertEquals(10, alarm.maxRingMinutes)
        assertFalse(alarm.vibrateWithHeadphones)
        assertFalse(alarm.enabled)
        assertTrue(gateway.armed.isEmpty())

        engine.ensureFirstRunAlarm(isEmpty = false, defaults = AppSettings())
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `saving arms the alarm and returns the next ring time`() = runTest {
        val (id, next) = engine.save(workday0630)
        assertEquals(bp(2026, 11, 9, 6, 30).ms(), next)
        assertEquals(next, gateway.main(id)!!.fireAt)
    }

    @Test
    fun `editing replaces the scheduled time instead of adding a second trigger`() = runTest {
        val (id, _) = engine.save(workday0630)
        engine.save(store.rows[id]!!.copy(hour = 7, minute = 10))
        assertEquals(1, gateway.armed.keys.count { it.first == id && it.second == TriggerKind.MAIN })
        assertEquals(bp(2026, 11, 9, 7, 10).ms(), gateway.main(id)!!.fireAt)
    }

    @Test
    fun `deleted alarm is unscheduled and a stale trigger is ignored`() = runTest {
        val (id, next) = engine.save(workday0630)
        engine.delete(id)
        assertTrue(gateway.armed.isEmpty())
        assertNull(store.get(id))
        assertNull(engine.onTrigger(id, TriggerKind.MAIN, next!!))
    }

    @Test
    fun `disabling cancels main and snooze`() = runTest {
        val (id, _) = engine.save(workday0630)
        fireMain(id)
        engine.snooze(listOf(id), 10)
        assertNotNull(gateway.snooze(id))
        engine.setEnabled(id, false)
        assertNull(gateway.main(id))
        assertNull(gateway.snooze(id))
        assertNull(store.get(id)!!.snoozeUntil)
    }

    @Test
    fun `trigger schedules the next repetition and a duplicate trigger is rejected`() = runTest {
        val (id, _) = engine.save(workday0630)
        val occurrence = gateway.main(id)!!.occurrence
        val ringing = fireMain(id)
        assertNotNull(ringing)
        assertEquals(bp(2026, 11, 10, 6, 30).ms(), gateway.main(id)!!.fireAt)
        // Same occurrence delivered again (e.g. re-armed late trigger): must not ring twice.
        assertNull(engine.onTrigger(id, TriggerKind.MAIN, occurrence))
    }

    @Test
    fun `dismiss keeps the next repetition scheduled`() = runTest {
        val (id, _) = engine.save(workday0630)
        fireMain(id)
        engine.dismiss(listOf(id))
        assertEquals(bp(2026, 11, 10, 6, 30).ms(), gateway.main(id)!!.fireAt)
        assertTrue(store.get(id)!!.enabled)
    }

    @Test
    fun `one-shot alarm switches off after it rang`() = runTest {
        val (id, _) = engine.save(workday0630.copy(days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 9)))
        assertNotNull(fireMain(id))
        assertFalse(store.get(id)!!.enabled)
        assertNull(gateway.main(id))
    }

    @Test
    fun `snooze arms a snooze trigger which rings again exactly once`() = runTest {
        val (id, _) = engine.save(workday0630)
        fireMain(id)
        val until = engine.snooze(listOf(id), 10)
        assertEquals(bp(2026, 11, 9, 6, 40).ms(), until)
        assertEquals(until, gateway.snooze(id)!!.fireAt)
        assertEquals(until, notifier.snoozed[id])

        clock.now = java.time.Instant.ofEpochMilli(until)
        assertNotNull(engine.onTrigger(id, TriggerKind.SNOOZE, until))
        assertNull(store.get(id)!!.snoozeUntil)
        assertNull(engine.onTrigger(id, TriggerKind.SNOOZE, until))
        assertFalse(notifier.snoozed.containsKey(id))
    }

    @Test
    fun `another alarm due during a snooze is scheduled independently`() = runTest {
        val (a, _) = engine.save(workday0630)
        val (b, _) = engine.save(workday0630.copy(label = "Második", minute = 35))
        fireMain(a) // 06:30
        engine.snooze(listOf(a), 10) // until 06:40
        assertEquals(bp(2026, 11, 9, 6, 35).ms(), gateway.main(b)!!.fireAt)
        assertEquals(bp(2026, 11, 9, 6, 40).ms(), gateway.snooze(a)!!.fireAt)
    }

    @Test
    fun `two alarms at the same time are both armed and both triggers are valid`() = runTest {
        val (a, _) = engine.save(workday0630)
        val (b, _) = engine.save(workday0630.copy(label = "Másik"))
        val occA = gateway.main(a)!!.occurrence
        val occB = gateway.main(b)!!.occurrence
        assertEquals(occA, occB)
        clock.now = java.time.Instant.ofEpochMilli(occA)
        assertNotNull(engine.onTrigger(a, TriggerKind.MAIN, occA))
        assertNotNull(engine.onTrigger(b, TriggerKind.MAIN, occB))
        // A merged session snoozes both together.
        engine.snooze(listOf(a, b), 5)
        assertNotNull(gateway.snooze(a))
        assertNotNull(gateway.snooze(b))
    }

    @Test
    fun `reboot re-arms everything and rings a slightly late alarm`() = runTest {
        val (id, next) = engine.save(workday0630)
        gateway.armed.clear() // AlarmManager forgets everything on reboot
        clock.set(bp(2026, 11, 9, 6, 33)) // phone was off at 06:30
        engine.rescheduleAll("BOOT_COMPLETED")
        val entry = gateway.main(id)!!
        assertEquals(next, entry.occurrence)
        assertEquals(clock.millis() + ReschedulePlanner.LATE_RING_DELAY_MS, entry.fireAt)
        assertNotNull(engine.onTrigger(id, TriggerKind.MAIN, entry.occurrence))
    }

    @Test
    fun `reboot long after the alarm reports it as missed`() = runTest {
        val (id, _) = engine.save(workday0630)
        gateway.armed.clear()
        clock.set(bp(2026, 11, 9, 11, 0))
        engine.rescheduleAll("BOOT_COMPLETED")
        assertEquals(1, notifier.missed.size)
        assertEquals(bp(2026, 11, 10, 6, 30).ms(), gateway.main(id)!!.fireAt)
    }

    @Test
    fun `time zone change re-arms at the same wall-clock time`() = runTest {
        val (id, _) = engine.save(workday0630)
        clock.setZone(ZoneId.of("Europe/London"))
        engine.rescheduleAll("TIMEZONE_CHANGED")
        // 05:00 Budapest = 04:00 London; next 06:30 London = 07:30 Budapest.
        assertEquals(bp(2026, 11, 9, 7, 30).ms(), gateway.main(id)!!.fireAt)
    }

    @Test
    fun `manual clock change forward reschedules from the new time`() = runTest {
        val (id, _) = engine.save(workday0630)
        clock.set(bp(2026, 11, 9, 6, 20))
        engine.rescheduleAll("TIME_SET")
        assertEquals(bp(2026, 11, 9, 6, 30).ms(), gateway.main(id)!!.fireAt)
    }

    @Test
    fun `enabling a past one-shot alarm moves it to the next occurrence`() = runTest {
        val (id, _) = engine.save(workday0630.copy(days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 20)))
        engine.setEnabled(id, false)
        store.save(store.get(id)!!.copy(oneShotDate = LocalDate.of(2026, 11, 1)))
        val next = engine.setEnabled(id, true)
        assertEquals(bp(2026, 11, 9, 6, 30).ms(), next)
    }

    @Test
    fun `save clamps snooze, volume and max ring time`() = runTest {
        val (id, _) = engine.save(workday0630.copy(snoozeMinutes = 0, volumePercent = 140, maxRingMinutes = 500, label = "  "))
        val saved = store.get(id)!!
        assertEquals(Alarm.MIN_SNOOZE, saved.snoozeMinutes)
        assertEquals(100, saved.volumePercent)
        assertEquals(Alarm.MAX_RING, saved.maxRingMinutes)
        assertEquals(Alarm.DEFAULT_LABEL, saved.label)
    }

    @Test
    fun `duplicate creates a disabled copy`() = runTest {
        val (id, _) = engine.save(workday0630)
        val copyId = engine.duplicate(id)!!
        val copy = store.get(copyId)!!
        assertFalse(copy.enabled)
        assertEquals("Munkahelyi ébresztés (másolat)", copy.label)
        assertNull(gateway.main(copyId))
    }

    @Test
    fun `test alarm is synthesised from settings`() = runTest {
        val at = engine.scheduleTest(1)
        assertEquals(clock.millis() + 60_000, at)
        val alarm = engine.onTrigger(AlarmEngine.TEST_ALARM_ID, TriggerKind.MAIN, at)!!
        assertEquals("Próbaébresztés", alarm.label)
        assertEquals(60, alarm.volumePercent)
    }
}
