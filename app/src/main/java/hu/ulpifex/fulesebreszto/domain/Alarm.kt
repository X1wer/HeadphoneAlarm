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
    /** User-given name; an empty name is replaced by the localized default when saved. */
    val label: String = "",
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
    /** Where the alarm may sound: only on verified headphones (default) or loudly on the speaker. */
    val output: AlarmOutput = AlarmOutput.HEADPHONES,
    /** Colour the alarm is shown in (ARGB); null means the default colour of its [output]. */
    val color: Int? = null,
    /** Playback options, used only when [sound] is a Zengő playlist. */
    val playlist: PlaylistOptions = PlaylistOptions(),
    /** The occurrence the main trigger is currently scheduled for. */
    val nextTriggerAt: Long? = null,
    /** The last occurrence that actually started ringing; it must never ring twice. */
    val lastTriggeredAt: Long? = null,
    /** When a pending snooze is due, or null when not snoozed. */
    val snoozeUntil: Long? = null,
    val createdAt: Long = 0,
) {
    val isRepeating: Boolean get() = !days.isEmpty
    val isLoud: Boolean get() = output == AlarmOutput.SPEAKER
    val isPlaylist: Boolean get() = sound is SoundRef.Zengo

    /** The colour actually shown: the chosen one, or red for loud and teal for headphone alarms. */
    val displayColor: Int get() = color ?: AlarmColors.defaultFor(output)

    companion object {
        const val MIN_SNOOZE = 1
        const val MAX_SNOOZE = 60
        const val MIN_RING = 1
        const val MAX_RING = 60

        /** The very first alarm shown after installation (disabled on purpose). */
        fun firstRunDefault(now: Long, label: String) = Alarm(
            label = label,
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
