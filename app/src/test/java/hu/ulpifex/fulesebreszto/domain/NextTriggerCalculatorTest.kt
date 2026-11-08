package hu.ulpifex.fulesebreszto.domain

import hu.ulpifex.fulesebreszto.bp
import hu.ulpifex.fulesebreszto.ms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NextTriggerCalculatorTest {

    private val weekdays0630 = Alarm(id = 1, hour = 6, minute = 30, days = WeekDays.WORKDAYS, enabled = true)
    private val daily = { h: Int, m: Int -> Alarm(id = 2, hour = h, minute = m, days = WeekDays.EVERY_DAY, enabled = true) }

    @Test
    fun `weekday alarm later today rings today`() {
        val next = NextTriggerCalculator.next(weekdays0630, bp(2026, 11, 9, 5, 0)) // Monday
        assertEquals(bp(2026, 11, 9, 6, 30), next)
    }

    @Test
    fun `alarm exactly now is not repeated - next occurrence is strictly later`() {
        val next = NextTriggerCalculator.next(weekdays0630, bp(2026, 11, 9, 6, 30))
        assertEquals(bp(2026, 11, 10, 6, 30), next)
    }

    @Test
    fun `friday after alarm skips the weekend to monday`() {
        val next = NextTriggerCalculator.next(weekdays0630, bp(2026, 11, 6, 7, 0)) // Friday
        assertEquals(bp(2026, 11, 9, 6, 30), next)
        assertEquals(DayOfWeek.MONDAY, next!!.dayOfWeek)
    }

    @Test
    fun `saturday and sunday are skipped for workday alarms`() {
        assertEquals(bp(2026, 11, 9, 6, 30), NextTriggerCalculator.next(weekdays0630, bp(2026, 11, 7, 6, 0)))
        assertEquals(bp(2026, 11, 9, 6, 30), NextTriggerCalculator.next(weekdays0630, bp(2026, 11, 8, 23, 59)))
    }

    @Test
    fun `custom selection of days`() {
        val tueThu = weekdays0630.copy(days = WeekDays.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals(bp(2026, 11, 10, 6, 30), NextTriggerCalculator.next(tueThu, bp(2026, 11, 9, 12, 0)))
        assertEquals(bp(2026, 11, 12, 6, 30), NextTriggerCalculator.next(tueThu, bp(2026, 11, 10, 6, 31)))
        // Only today's weekday selected and it has already passed: one week later.
        val mondayOnly = weekdays0630.copy(days = WeekDays.of(DayOfWeek.MONDAY))
        assertEquals(bp(2026, 11, 16, 6, 30), NextTriggerCalculator.next(mondayOnly, bp(2026, 11, 9, 7, 0)))
    }

    @Test
    fun `month and year rollover`() {
        assertEquals(bp(2026, 11, 2, 6, 30), NextTriggerCalculator.next(weekdays0630, bp(2026, 10, 30, 7, 0)))
        assertEquals(bp(2027, 1, 1, 6, 30), NextTriggerCalculator.next(daily(6, 30), bp(2026, 12, 31, 7, 0)))
        assertEquals(bp(2028, 2, 29, 6, 30), NextTriggerCalculator.next(daily(6, 30), bp(2028, 2, 28, 7, 0)))
    }

    @Test
    fun `spring forward - nonexistent 02_30 is moved to 03_30 CEST`() {
        // 2026-03-29 02:00 CET -> 03:00 CEST in Budapest.
        val next = NextTriggerCalculator.next(daily(2, 30), bp(2026, 3, 28, 23, 0))!!
        assertEquals(LocalDate.of(2026, 3, 29), next.toLocalDate())
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next.toInstant())
    }

    @Test
    fun `spring forward does not disturb a normal 06_30 alarm`() {
        val next = NextTriggerCalculator.next(daily(6, 30), bp(2026, 3, 28, 23, 0))!!
        assertEquals(Instant.parse("2026-03-29T04:30:00Z"), next.toInstant()) // 06:30 CEST
    }

    @Test
    fun `fall back - ambiguous 02_30 rings once on the first occurrence`() {
        // 2026-10-25 03:00 CEST -> 02:00 CET: 02:30 happens twice.
        val first = NextTriggerCalculator.next(daily(2, 30), bp(2026, 10, 25, 0, 0))!!
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), first.toInstant())
        assertEquals(ZoneOffset.ofHours(2), first.offset)

        // During the repeated hour (02:10 CET) the second 02:30 must not ring.
        val repeatedHour = ZonedDateTime.ofInstant(Instant.parse("2026-10-25T01:10:00Z"), first.zone)
        val second = NextTriggerCalculator.next(daily(2, 30).copy(lastTriggeredAt = first.ms()), repeatedHour)!!
        assertEquals(LocalDate.of(2026, 10, 26), second.toLocalDate())
    }

    @Test
    fun `time zone change keeps wall-clock time`() {
        val instant = Instant.parse("2026-11-08T10:00:00Z")
        val inBudapest = NextTriggerCalculator.next(daily(6, 30), instant.atZone(ZoneId.of("Europe/Budapest")))!!
        val inNewYork = NextTriggerCalculator.next(daily(6, 30), instant.atZone(ZoneId.of("America/New_York")))!!
        assertEquals(Instant.parse("2026-11-09T05:30:00Z"), inBudapest.toInstant())
        assertEquals(Instant.parse("2026-11-08T11:30:00Z"), inNewYork.toInstant())
        assertEquals(6, inNewYork.hour)
        assertEquals(30, inNewYork.minute)
    }

    @Test
    fun `one-shot alarm with date`() {
        val oneShot = Alarm(id = 3, hour = 7, minute = 15, days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 20), enabled = true)
        assertEquals(bp(2026, 11, 20, 7, 15), NextTriggerCalculator.next(oneShot, bp(2026, 11, 8, 12, 0)))
        assertNull(NextTriggerCalculator.next(oneShot, bp(2026, 11, 20, 7, 15)))
        assertNull(NextTriggerCalculator.next(oneShot, bp(2026, 11, 21, 0, 0)))
    }

    @Test
    fun `one-shot alarm without date rings at the next occurrence of its time`() {
        val oneShot = Alarm(id = 4, hour = 7, minute = 0, days = WeekDays.NONE, enabled = true)
        assertEquals(bp(2026, 11, 8, 7, 0), NextTriggerCalculator.next(oneShot, bp(2026, 11, 8, 6, 0)))
        assertEquals(bp(2026, 11, 9, 7, 0), NextTriggerCalculator.next(oneShot, bp(2026, 11, 8, 8, 0)))
        assertEquals(LocalDate.of(2026, 11, 8), NextTriggerCalculator.defaultOneShotDate(7, 0, bp(2026, 11, 8, 6, 0)))
        assertEquals(LocalDate.of(2026, 11, 9), NextTriggerCalculator.defaultOneShotDate(7, 0, bp(2026, 11, 8, 7, 0)))
    }

    @Test
    fun `dismissed occurrence does not ring again after the clock is set back`() {
        val rang = bp(2026, 11, 9, 6, 30)
        val alarm = weekdays0630.copy(lastTriggeredAt = rang.ms())
        // User sets the clock back to 06:00 the same morning.
        assertEquals(bp(2026, 11, 10, 6, 30), NextTriggerCalculator.next(alarm, bp(2026, 11, 9, 6, 0)))
    }

    @Test
    fun `guard is ignored when the clock is set back by days`() {
        val alarm = weekdays0630.copy(lastTriggeredAt = bp(2026, 11, 16, 6, 30).ms())
        assertEquals(bp(2026, 11, 9, 6, 30), NextTriggerCalculator.next(alarm, bp(2026, 11, 9, 5, 0)))
    }

    @Test
    fun `nearest ring across several alarms including snooze`() {
        val now = bp(2026, 11, 9, 6, 0)
        val a = weekdays0630.copy(id = 10)
        val b = daily(6, 30).copy(id = 5) // same time, lower id wins the tie
        val c = daily(6, 10).copy(id = 7, enabled = false, snoozeUntil = bp(2026, 11, 9, 6, 20).ms())
        val next = UpcomingAlarms.nextRing(listOf(a, b, c), now)!!
        assertEquals(7L, next.alarm.id)
        assertEquals(true, next.isSnooze)

        val withoutSnooze = UpcomingAlarms.nextRing(listOf(a, b), now)!!
        assertEquals(5L, withoutSnooze.alarm.id)
        assertNull(UpcomingAlarms.nextRing(listOf(a.copy(enabled = false)), now))
    }

    @Test
    fun `list is ordered by time of day`() {
        val sorted = UpcomingAlarms.sortedForList(
            listOf(daily(8, 0).copy(id = 1), daily(6, 30).copy(id = 2), daily(6, 5).copy(id = 3), daily(6, 30).copy(id = 0)),
        )
        assertEquals(listOf(3L, 0L, 2L, 1L), sorted.map { it.id })
    }
}
