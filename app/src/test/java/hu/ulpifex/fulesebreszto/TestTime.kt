package hu.ulpifex.fulesebreszto

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

val BUDAPEST: ZoneId = ZoneId.of("Europe/Budapest")

fun bp(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
    ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), BUDAPEST)

fun ZonedDateTime.ms(): Long = toInstant().toEpochMilli()

/** A clock whose instant and zone can be changed by the test. */
class MutableClock(var now: Instant, private var currentZone: ZoneId = BUDAPEST) : Clock() {
    override fun getZone(): ZoneId = currentZone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun set(time: ZonedDateTime) {
        now = time.toInstant()
    }

    fun setZone(zone: ZoneId) {
        currentZone = zone
    }

    fun advanceMinutes(minutes: Long) {
        now = now.plusSeconds(minutes * 60)
    }
}
