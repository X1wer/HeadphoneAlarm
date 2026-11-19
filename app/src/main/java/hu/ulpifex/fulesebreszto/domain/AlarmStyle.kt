package hu.ulpifex.fulesebreszto.domain

/** Where an alarm may produce sound. */
enum class AlarmOutput {
    /** Only on allowed, verified headphones; otherwise vibration (the app's original behaviour). */
    HEADPHONES,

    /** A normal, loud alarm on the phone speaker (alarm stream), like a classic alarm clock. */
    SPEAKER,
}

/** The colour palette offered for alarms (ARGB). */
object AlarmColors {
    const val TEAL = 0xFF4DD0E1.toInt()
    const val BLUE = 0xFF64B5F6.toInt()
    const val INDIGO = 0xFF7986CB.toInt()
    const val PURPLE = 0xFFBA68C8.toInt()
    const val PINK = 0xFFF06292.toInt()
    const val RED = 0xFFE53935.toInt()
    const val ORANGE = 0xFFFF9800.toInt()
    const val AMBER = 0xFFFFCA28.toInt()
    const val GREEN = 0xFF66BB6A.toInt()
    const val GREY = 0xFF90A4AE.toInt()

    val PALETTE = listOf(TEAL, BLUE, INDIGO, PURPLE, PINK, RED, ORANGE, AMBER, GREEN, GREY)

    fun defaultFor(output: AlarmOutput): Int = if (output == AlarmOutput.SPEAKER) RED else TEAL
}

/** How a Zengő playlist alarm orders its songs. */
enum class PlaylistOrder { FOLLOW_APP, IN_ORDER, SHUFFLE }

/** What a Zengő playlist alarm does when the list has played to its end. */
enum class PlaylistEnd { FOLLOW_APP, RESTART_LIST, REPEAT_LAST, BUILTIN_TONE, VIBRATE_ONLY, STOP_ALARM }

/**
 * Options of a playlist alarm. [FOLLOW_APP][PlaylistOrder.FOLLOW_APP] values use the shuffle and
 * repeat mode saved in Zengő at the moment the alarm rings.
 */
data class PlaylistOptions(
    val order: PlaylistOrder = PlaylistOrder.FOLLOW_APP,
    val end: PlaylistEnd = PlaylistEnd.FOLLOW_APP,
    /** The song the alarm always starts with; null = the start of the list (random when shuffled). */
    val startTrackId: Long? = null,
    val startTrackTitle: String? = null,
)
