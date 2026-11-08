package hu.ulpifex.fulesebreszto.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Hungarian, locale-independent formatting helpers used by the UI and notifications. */
object HuFormat {
    private val shortDays = mapOf(
        DayOfWeek.MONDAY to "H",
        DayOfWeek.TUESDAY to "K",
        DayOfWeek.WEDNESDAY to "Sze",
        DayOfWeek.THURSDAY to "Cs",
        DayOfWeek.FRIDAY to "P",
        DayOfWeek.SATURDAY to "Szo",
        DayOfWeek.SUNDAY to "V",
    )
    private val longDays = mapOf(
        DayOfWeek.MONDAY to "hétfő",
        DayOfWeek.TUESDAY to "kedd",
        DayOfWeek.WEDNESDAY to "szerda",
        DayOfWeek.THURSDAY to "csütörtök",
        DayOfWeek.FRIDAY to "péntek",
        DayOfWeek.SATURDAY to "szombat",
        DayOfWeek.SUNDAY to "vasárnap",
    )
    private val months = listOf(
        "jan.", "febr.", "márc.", "ápr.", "máj.", "jún.",
        "júl.", "aug.", "szept.", "okt.", "nov.", "dec.",
    )

    fun shortDay(day: DayOfWeek): String = shortDays.getValue(day)
    fun longDay(day: DayOfWeek): String = longDays.getValue(day)

    fun time(hour: Int, minute: Int, use24h: Boolean = true): String =
        if (use24h) {
            "%02d:%02d".format(hour, minute)
        } else {
            val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
            val suffix = if (hour < 12) "de." else "du."
            "$h12:%02d $suffix".format(minute)
        }

    fun date(date: LocalDate): String =
        "${date.year}. ${months[date.monthValue - 1]} ${date.dayOfMonth}. (${longDay(date.dayOfWeek)})"

    /** "H K Sze Cs P", "Minden nap", "Hétköznap", "Hétvége" or the one-shot date. */
    fun repeatSummary(alarm: Alarm): String = when {
        alarm.days == WeekDays.EVERY_DAY -> "Minden nap"
        alarm.days == WeekDays.WORKDAYS -> "H K Sze Cs P"
        alarm.days == WeekDays.WEEKEND -> "Szo V"
        !alarm.days.isEmpty -> alarm.days.toList().joinToString(" ") { shortDay(it) }
        alarm.oneShotDate != null -> "Egyszeri · ${date(alarm.oneShotDate)}"
        else -> "Egyszeri"
    }

    /** "ma 06:30", "holnap 06:30", "hétfő 06:30" or "2026. nov. 20. (péntek) 06:30". */
    fun relativeDayTime(at: ZonedDateTime, now: ZonedDateTime, use24h: Boolean = true): String {
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        val time = time(at.hour, at.minute, use24h)
        return when {
            days == 0L -> "ma $time"
            days == 1L -> "holnap $time"
            days in 2..6 -> "${longDay(at.dayOfWeek)} $time"
            else -> "${date(at.toLocalDate())} $time"
        }
    }

    /** "8 óra 12 perc múlva", "3 perc múlva", "kevesebb mint 1 perc múlva". */
    fun countdown(fromMs: Long, toMs: Long): String {
        val totalMinutes = (toMs - fromMs).coerceAtLeast(0) / 60_000
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        if (totalMinutes == 0L) return "kevesebb mint 1 perc múlva"
        val parts = buildList {
            if (days > 0) add("$days nap")
            if (hours > 0) add("$hours óra")
            if (minutes > 0) add("$minutes perc")
        }
        return parts.joinToString(" ") + " múlva"
    }
}

/** Maps the volume slider (0–100 %) to a MediaPlayer gain with a perceptual curve. */
object VolumeCurve {
    fun gain(percent: Int): Float {
        val p = percent.coerceIn(0, 100) / 100f
        return p * p
    }
}
