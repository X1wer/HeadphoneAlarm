package hu.ulpifex.fulesebreszto.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SoundResolverTest {

    private val custom = SoundRef.Custom("content://com.android.providers.media.documents/document/audio%3A42")

    @Test
    fun `built-in sound is used as is`() {
        val r = SoundResolver.resolve(BuiltInSounds.SOFT.ref) { error("must not be called") }
        assertEquals(BuiltInSounds.SOFT.ref, r.ref)
        assertFalse(r.usedFallback)
    }

    @Test
    fun `readable custom file is used`() {
        val r = SoundResolver.resolve(custom) { true }
        assertEquals(custom, r.ref)
        assertFalse(r.usedFallback)
    }

    @Test
    fun `missing custom file falls back to the built-in tone`() {
        val r = SoundResolver.resolve(custom) { false }
        assertEquals(BuiltInSounds.FALLBACK.ref, r.ref)
        assertTrue(r.usedFallback)
    }

    @Test
    fun `revoked permission (exception) also falls back`() {
        val r = SoundResolver.resolve(custom) { throw SecurityException("Permission Denial") }
        assertEquals(BuiltInSounds.FALLBACK.ref, r.ref)
        assertTrue(r.usedFallback)
    }

    @Test
    fun `sound references survive serialisation`() {
        BuiltInSounds.ALL.forEach { assertEquals(it.ref, SoundRef.parse(it.ref.serialized)) }
        assertEquals(custom, SoundRef.parse(custom.serialized))
        assertEquals(BuiltInSounds.DEFAULT.ref, SoundRef.parse(null))
        assertEquals(BuiltInSounds.DEFAULT.ref, SoundRef.parse("builtin:does-not-exist"))
        assertTrue(BuiltInSounds.ALL.size >= 3)
    }
}

class FormattingTest {

    private val hu = TimeFormat.HU
    private val en = TimeFormat.EN

    @Test
    fun `24 and 12 hour times`() {
        assertEquals("06:30", hu.time(6, 30))
        assertEquals("00:05", hu.time(0, 5))
        assertEquals("6:30 de.", hu.time(6, 30, use24h = false))
        assertEquals("12:00 du.", hu.time(12, 0, use24h = false))
        assertEquals("12:15 de.", hu.time(0, 15, use24h = false))
        assertEquals("06:30", en.time(6, 30))
        assertEquals("6:30 AM", en.time(6, 30, use24h = false))
        assertEquals("12:00 PM", en.time(12, 0, use24h = false))
    }

    @Test
    fun `repeat summaries`() {
        val base = Alarm(hour = 6, minute = 30)
        assertEquals("H K Sze Cs P", hu.repeatSummary(base.copy(days = WeekDays.WORKDAYS)))
        assertEquals("Minden nap", hu.repeatSummary(base.copy(days = WeekDays.EVERY_DAY)))
        assertEquals("Szo V", hu.repeatSummary(base.copy(days = WeekDays.WEEKEND)))
        assertEquals("K Cs", hu.repeatSummary(base.copy(days = WeekDays.of(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY))))
        assertEquals(
            "Egyszeri · 2026. nov. 20. (péntek)",
            hu.repeatSummary(base.copy(days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 20))),
        )
        assertEquals("Mon–Fri", en.repeatSummary(base.copy(days = WeekDays.WORKDAYS)))
        assertEquals("Every day", en.repeatSummary(base.copy(days = WeekDays.EVERY_DAY)))
        assertEquals("Tue Thu", en.repeatSummary(base.copy(days = WeekDays.of(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY))))
        assertEquals(
            "Once · Fri, Nov 20, 2026",
            en.repeatSummary(base.copy(days = WeekDays.NONE, oneShotDate = LocalDate.of(2026, 11, 20))),
        )
    }

    @Test
    fun `relative day and countdown`() {
        val zone = ZoneId.of("Europe/Budapest")
        val now = ZonedDateTime.of(2026, 11, 6, 22, 0, 0, 0, zone) // Friday
        assertEquals("ma 23:00", hu.relativeDayTime(now.plusHours(1), now))
        assertEquals("holnap 06:30", hu.relativeDayTime(now.plusDays(1).withHour(6).withMinute(30), now))
        assertEquals("hétfő 06:30", hu.relativeDayTime(now.plusDays(3).withHour(6).withMinute(30), now))
        assertEquals("today 23:00", en.relativeDayTime(now.plusHours(1), now))
        assertEquals("tomorrow 06:30", en.relativeDayTime(now.plusDays(1).withHour(6).withMinute(30), now))
        assertEquals("Monday 06:30", en.relativeDayTime(now.plusDays(3).withHour(6).withMinute(30), now))
        assertEquals("Fri, Nov 20, 2026 06:30", en.relativeDayTime(now.plusDays(14).withHour(6).withMinute(30), now))
        val ms = now.toInstant().toEpochMilli()
        assertEquals("8 óra 30 perc múlva", hu.countdown(ms, ms + (8 * 60 + 30) * 60_000L))
        assertEquals("1 nap 2 óra múlva", hu.countdown(ms, ms + 26 * 3_600_000L))
        assertEquals("kevesebb mint 1 perc múlva", hu.countdown(ms, ms + 30_000))
        assertEquals("in 8 h 30 min", en.countdown(ms, ms + (8 * 60 + 30) * 60_000L))
        assertEquals("in 1 day 2 h", en.countdown(ms, ms + 26 * 3_600_000L))
        assertEquals("in 3 days", en.countdown(ms, ms + 72 * 3_600_000L))
        assertEquals("in less than a minute", en.countdown(ms, ms + 30_000))
    }

    @Test
    fun `volume curve is monotonic from silent to full`() {
        assertEquals(0f, VolumeCurve.gain(0))
        assertEquals(1f, VolumeCurve.gain(100))
        assertEquals(0.36f, VolumeCurve.gain(60), 0.0001f)
        assertEquals(1f, VolumeCurve.gain(150))
        (0 until 100 step 5).forEach { assertTrue(VolumeCurve.gain(it) < VolumeCurve.gain(it + 5)) }
    }

    @Test
    fun `weekday mask helpers`() {
        assertTrue(DayOfWeek.MONDAY in WeekDays.WORKDAYS)
        assertTrue(DayOfWeek.FRIDAY in WeekDays.WORKDAYS)
        assertFalse(DayOfWeek.SATURDAY in WeekDays.WORKDAYS)
        assertFalse(DayOfWeek.SUNDAY in WeekDays.WORKDAYS)
        val toggled = WeekDays.WORKDAYS.toggle(DayOfWeek.FRIDAY).toggle(DayOfWeek.SUNDAY)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            toggled.toList(),
        )
        assertTrue(WeekDays.NONE.isEmpty)
        assertEquals(0x7F, WeekDays.EVERY_DAY.mask)
    }
}
