package hu.ulpifex.fulesebreszto.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One alarm as the user configured it, plus the scheduling bookkeeping the engine needs.
 *
 * Instants are epoch milliseconds; wall-clock values (hour, minute, days, date) are always
 * interpreted in the device's current time zone at scheduling time.
 */
data class Alarm(
    val id: Long = 0,
    val label: String = DEFAULT_LABEL,
    val hour: Int = 6,
    val minute: Int = 30,
    val days: WeekDays = WeekDays.WORKDAYS,
    /** Only used when [days] is empty: the calendar day of a one-shot alarm. */
    val oneShotDate: LocalDate? = null,
    val enabled: Boolean = false,
    val sound: SoundRef = BuiltInSounds.DEFAULT.ref,
    val soundName: String = BuiltInSounds.DEFAULT.displayName,
    val volumePercent: Int = 60,
    val vibrateWithHeadphones: Boolean = false,
    val snoozeMinutes: Int = 10,
    val maxRingMinutes: Int = 10,
    /** The occurrence the main trigger is currently scheduled for. */
    val nextTriggerAt: Long? = null,
    /** The last occurrence that actually started ringing; it must never ring twice. */
    val lastTriggeredAt: Long? = null,
    /** When a pending snooze is due, or null when not snoozed. */
    val snoozeUntil: Long? = null,
    val createdAt: Long = 0,
) {
    val isRepeating: Boolean get() = !days.isEmpty

    companion object {
        const val DEFAULT_LABEL = "Ébresztés"
        const val MIN_SNOOZE = 1
        const val MAX_SNOOZE = 60
        const val MIN_RING = 1
        const val MAX_RING = 60

        /** The very first alarm shown after installation (disabled on purpose). */
        fun firstRunDefault(now: Long) = Alarm(
            label = DEFAULT_LABEL,
            hour = 6,
            minute = 30,
            days = WeekDays.WORKDAYS,
            enabled = false,
            createdAt = now,
        )
    }
}

/** Bit set of weekdays; bit 0 = Monday … bit 6 = Sunday (ISO order). */
@JvmInline
value class WeekDays(val mask: Int) {
    init {
        require(mask in 0..0x7F) { "Invalid weekday mask: $mask" }
    }

    val isEmpty: Boolean get() = mask == 0

    operator fun contains(day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun with(day: DayOfWeek, selected: Boolean): WeekDays =
        WeekDays(if (selected) mask or bit(day) else mask and bit(day).inv())

    fun toggle(day: DayOfWeek): WeekDays = with(day, day !in this)

    fun toList(): List<DayOfWeek> = DayOfWeek.entries.filter { it in this }

    companion object {
        val NONE = WeekDays(0)
        val WORKDAYS = of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND = of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val EVERY_DAY = WeekDays(0x7F)

        fun of(vararg days: DayOfWeek): WeekDays =
            WeekDays(days.fold(0) { acc, d -> acc or bit(d) })

        private fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}
