package hu.ulpifex.fulesebreszto.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Languages the app's own texts are available in. */
enum class TextLang { HU, EN }

/**
 * Locale-independent date/time formatting for the app's two languages. Pure Kotlin so the
 * exact wording is unit-tested; everything else lives in Android string resources.
 */
class TimeFormat private constructor(val lang: TextLang) {

    private val hu = lang == TextLang.HU

    fun shortDay(day: DayOfWeek): String = (if (hu) HU_SHORT else EN_SHORT)[day.value - 1]
    fun longDay(day: DayOfWeek): String = (if (hu) HU_LONG else EN_LONG)[day.value - 1]

    fun time(hour: Int, minute: Int, use24h: Boolean = true): String {
        if (use24h) return "%02d:%02d".format(hour, minute)
        val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
        val suffix = if (hu) (if (hour < 12) "de." else "du.") else (if (hour < 12) "AM" else "PM")
        return "$h12:%02d $suffix".format(minute)
    }

    /** "2026. nov. 20. (péntek)" / "Fri, Nov 20, 2026". */
    fun date(date: LocalDate): String =
        if (hu) {
            "${date.year}. ${HU_MONTHS[date.monthValue - 1]} ${date.dayOfMonth}. (${longDay(date.dayOfWeek)})"
        } else {
            "${shortDay(date.dayOfWeek)}, ${EN_MONTHS[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}"
        }

    fun repeatSummary(alarm: Alarm): String = when {
        alarm.days == WeekDays.EVERY_DAY -> if (hu) "Minden nap" else "Every day"
        alarm.days == WeekDays.WORKDAYS -> if (hu) "H K Sze Cs P" else "Mon–Fri"
        alarm.days == WeekDays.WEEKEND -> if (hu) "Szo V" else "Sat Sun"
        !alarm.days.isEmpty -> alarm.days.toList().joinToString(" ") { shortDay(it) }
        alarm.oneShotDate != null -> (if (hu) "Egyszeri · " else "Once · ") + date(alarm.oneShotDate)
        else -> if (hu) "Egyszeri" else "Once"
    }

    /** "ma 06:30" / "today 06:30", "holnap" / "tomorrow", weekday within a week, else the date. */
    fun relativeDayTime(at: ZonedDateTime, now: ZonedDateTime, use24h: Boolean = true): String {
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        val time = time(at.hour, at.minute, use24h)
        return when {
            days == 0L -> (if (hu) "ma " else "today ") + time
            days == 1L -> (if (hu) "holnap " else "tomorrow ") + time
            days in 2..6 -> "${longDay(at.dayOfWeek)} $time"
            else -> "${date(at.toLocalDate())} $time"
        }
    }

    /** "8 óra 12 perc múlva" / "in 8 h 12 min". */
    fun countdown(fromMs: Long, toMs: Long): String {
        val totalMinutes = (toMs - fromMs).coerceAtLeast(0) / 60_000
        if (totalMinutes == 0L) return if (hu) "kevesebb mint 1 perc múlva" else "in less than a minute"
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        val parts = buildList {
            if (days > 0) add(if (hu) "$days nap" else if (days == 1L) "1 day" else "$days days")
            if (hours > 0) add(if (hu) "$hours óra" else "$hours h")
            if (minutes > 0) add(if (hu) "$minutes perc" else "$minutes min")
        }.joinToString(" ")
        return if (hu) "$parts múlva" else "in $parts"
    }

    companion object {
        val HU = TimeFormat(TextLang.HU)
        val EN = TimeFormat(TextLang.EN)

        fun of(lang: TextLang): TimeFormat = if (lang == TextLang.HU) HU else EN

        private val HU_SHORT = listOf("H", "K", "Sze", "Cs", "P", "Szo", "V")
        private val HU_LONG = listOf("hétfő", "kedd", "szerda", "csütörtök", "péntek", "szombat", "vasárnap")
        private val HU_MONTHS = listOf(
            "jan.", "febr.", "márc.", "ápr.", "máj.", "jún.", "júl.", "aug.", "szept.", "okt.", "nov.", "dec.",
        )
        private val EN_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val EN_LONG = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val EN_MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
    }
}

/** Maps the volume slider (0–100 %) to a MediaPlayer gain with a perceptual curve. */
object VolumeCurve {
    fun gain(percent: Int): Float {
        val p = percent.coerceIn(0, 100) / 100f
        return p * p
    }
}
