package hu.ulpifex.fulesebreszto.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Computes the next wall-clock occurrence of an alarm.
 *
 * DST handling relies on [ZonedDateTime.of]: a time inside a spring-forward gap is moved
 * forward by the gap length (02:30 → 03:30), and a time inside a fall-back overlap uses the
 * earlier offset, so the alarm rings once, on the first 02:30.
 */
object NextTriggerCalculator {

    /**
     * How far ahead of "now" the last triggered occurrence may be and still block re-ringing.
     * It protects against ringing the same occurrence again after the clock is set back a bit,
     * without blocking alarms for days if the clock is set back a lot.
     */
    private const val GUARD_WINDOW_MS = 36L * 60 * 60 * 1000

    fun next(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        val nowInstant = now.toInstant()
        val guard = alarm.lastTriggeredAt
            ?.let(Instant::ofEpochMilli)
            ?.takeIf { it.isAfter(nowInstant) && ChronoUnit.MILLIS.between(nowInstant, it) <= GUARD_WINDOW_MS }
        val threshold = guard ?: nowInstant
        val time = LocalTime.of(alarm.hour, alarm.minute)

        fun isValid(candidate: ZonedDateTime): Boolean {
            val instant = candidate.toInstant()
            if (!instant.isAfter(threshold)) return false
            return alarm.lastTriggeredAt == null || instant.toEpochMilli() != alarm.lastTriggeredAt
        }

        if (!alarm.isRepeating) {
            val date = alarm.oneShotDate
            if (date != null) {
                val candidate = ZonedDateTime.of(date, time, zone)
                return candidate.takeIf(::isValid)
            }
            // A one-shot alarm without a date rings at the next occurrence of its time.
            val startDate = threshold.atZone(zone).toLocalDate()
            for (offset in 0L..2L) {
                val candidate = ZonedDateTime.of(startDate.plusDays(offset), time, zone)
                if (isValid(candidate)) return candidate
            }
            return null
        }

        val startDate = threshold.atZone(zone).toLocalDate()
        // Eight days covers "today already passed, only today's weekday selected".
        for (offset in 0L..8L) {
            val date = startDate.plusDays(offset)
            if (date.dayOfWeek !in alarm.days) continue
            val candidate = ZonedDateTime.of(date, time, zone)
            if (isValid(candidate)) return candidate
        }
        return null
    }

    fun nextMillis(alarm: Alarm, now: ZonedDateTime): Long? = next(alarm, now)?.toInstant()?.toEpochMilli()

    /** The default date for a new one-shot alarm: today if the time is still ahead, else tomorrow. */
    fun defaultOneShotDate(hour: Int, minute: Int, now: ZonedDateTime): LocalDate {
        val today = ZonedDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute), now.zone)
        return if (today.toInstant().isAfter(now.toInstant())) now.toLocalDate() else now.toLocalDate().plusDays(1)
    }
}

/** A scheduled ring (main occurrence or snooze) belonging to a specific alarm. */
data class UpcomingRing(val alarm: Alarm, val at: Long, val isSnooze: Boolean)

object UpcomingAlarms {
    /** The soonest ring among all enabled alarms and pending snoozes, ties broken by id. */
    fun nextRing(alarms: List<Alarm>, now: ZonedDateTime): UpcomingRing? {
        val nowMs = now.toInstant().toEpochMilli()
        return alarms.flatMap { alarm ->
            buildList {
                if (alarm.enabled) {
                    NextTriggerCalculator.nextMillis(alarm, now)?.let { add(UpcomingRing(alarm, it, false)) }
                }
                alarm.snoozeUntil?.takeIf { it > nowMs }?.let { add(UpcomingRing(alarm, it, true)) }
            }
        }.minWithOrNull(compareBy<UpcomingRing> { it.at }.thenBy { it.alarm.id })
    }

    /** Alarms in the order shown on the main screen: by time of day, then by id. */
    fun sortedForList(alarms: List<Alarm>): List<Alarm> =
        alarms.sortedWith(compareBy<Alarm> { it.hour }.thenBy { it.minute }.thenBy { it.id })
}
