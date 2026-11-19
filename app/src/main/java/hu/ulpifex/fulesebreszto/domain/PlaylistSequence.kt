package hu.ulpifex.fulesebreszto.domain

import kotlin.random.Random

/** Zengő's saved playback mode, as its provider reports it ("ALL", "ONE", "OFF", "STOP_AFTER_CURRENT"). */
data class ZengoMode(val shuffle: Boolean, val repeat: String)

/** End behaviour after all [FOLLOW_APP][PlaylistEnd.FOLLOW_APP] choices are resolved. */
enum class ResolvedEnd { RESTART_LIST, REPEAT_LAST, BUILTIN_TONE, VIBRATE_ONLY, STOP_ALARM }

/** The concrete plan a ringing playlist alarm follows. */
data class PlaylistPlan(
    val shuffle: Boolean,
    /** Zengő "repeat one": the first song is repeated for the whole alarm. */
    val repeatOne: Boolean,
    /** Zengő "stop after the current song": only the first song plays, then [end] applies. */
    val stopAfterFirst: Boolean,
    val end: ResolvedEnd,
)

object PlaylistPlanner {
    /**
     * Combines the alarm's own choices with Zengő's saved mode. Without a saved mode (Zengő not
     * reachable) "follow the app" means in order, restarting the list.
     */
    fun resolve(options: PlaylistOptions, zengo: ZengoMode?): PlaylistPlan {
        val shuffle = when (options.order) {
            PlaylistOrder.IN_ORDER -> false
            PlaylistOrder.SHUFFLE -> true
            PlaylistOrder.FOLLOW_APP -> zengo?.shuffle ?: false
        }
        if (options.end != PlaylistEnd.FOLLOW_APP) {
            val end = when (options.end) {
                PlaylistEnd.RESTART_LIST -> ResolvedEnd.RESTART_LIST
                PlaylistEnd.REPEAT_LAST -> ResolvedEnd.REPEAT_LAST
                PlaylistEnd.BUILTIN_TONE -> ResolvedEnd.BUILTIN_TONE
                PlaylistEnd.VIBRATE_ONLY -> ResolvedEnd.VIBRATE_ONLY
                PlaylistEnd.STOP_ALARM, PlaylistEnd.FOLLOW_APP -> ResolvedEnd.STOP_ALARM
            }
            return PlaylistPlan(shuffle, repeatOne = false, stopAfterFirst = false, end = end)
        }
        return when (zengo?.repeat) {
            "ONE" -> PlaylistPlan(shuffle, repeatOne = true, stopAfterFirst = false, end = ResolvedEnd.RESTART_LIST)
            // Zengő stops at the end; the alarm then keeps vibrating instead of going silent.
            "OFF" -> PlaylistPlan(shuffle, repeatOne = false, stopAfterFirst = false, end = ResolvedEnd.VIBRATE_ONLY)
            "STOP_AFTER_CURRENT" -> PlaylistPlan(shuffle, repeatOne = false, stopAfterFirst = true, end = ResolvedEnd.VIBRATE_ONLY)
            else -> PlaylistPlan(shuffle, repeatOne = false, stopAfterFirst = false, end = ResolvedEnd.RESTART_LIST)
        }
    }
}

/** What to do after a song has finished. */
sealed interface NextSong {
    data class Play(val index: Int) : NextSong
    data class End(val end: ResolvedEnd) : NextSong
}

/**
 * The order in which a playlist alarm plays its songs ([tracks] are list positions' track ids).
 *
 * In order, the alarm starts at [startTrackId] (or the first song) and plays to the end of the list;
 * shuffled, the start song comes first and the rest follows in random order. When the list is
 * exhausted, [PlaylistPlan.end] decides: restart (from the top / reshuffled), repeat the last song,
 * or end with another action.
 */
class PlaylistSequence(
    private val tracks: List<Long>,
    private val plan: PlaylistPlan,
    startTrackId: Long?,
    private val random: Random = Random.Default,
) {
    init {
        require(tracks.isNotEmpty()) { "empty playlist" }
    }

    private val startIndex: Int? = startTrackId?.let { id -> tracks.indexOf(id).takeIf { it >= 0 } }
    private var order: List<Int> = firstOrder()
    private var position = 0

    /** Index (into [tracks]) of the song to play now. */
    val current: Int get() = order[position]

    private fun firstOrder(): List<Int> {
        val all = tracks.indices.toList()
        return if (plan.shuffle) {
            val first = startIndex ?: all.random(random)
            listOf(first) + (all - first).shuffled(random)
        } else {
            all.drop(startIndex ?: 0)
        }
    }

    private fun restartOrder(): List<Int> {
        val all = tracks.indices.toList()
        if (!plan.shuffle) return all
        // Avoid playing the same song twice in a row across the restart.
        val last = order.last()
        val reshuffled = all.shuffled(random)
        return if (reshuffled.size > 1 && reshuffled.first() == last) reshuffled.drop(1) + last else reshuffled
    }

    /** Called when the current song has finished playing. Songs found broken earlier are skipped. */
    fun next(): NextSong {
        if (plan.repeatOne) return NextSong.Play(current)
        if (plan.stopAfterFirst) return NextSong.End(plan.end)
        repeat(tracks.size * 2 + 2) {
            if (position + 1 < order.size) {
                position++
            } else {
                when (plan.end) {
                    ResolvedEnd.RESTART_LIST -> {
                        order = restartOrder()
                        position = 0
                    }
                    // The song that just finished played fine, so it can be repeated.
                    ResolvedEnd.REPEAT_LAST -> return NextSong.Play(current)
                    else -> return NextSong.End(plan.end)
                }
            }
            if (current !in broken) return NextSong.Play(current)
        }
        return NextSong.End(ResolvedEnd.BUILTIN_TONE)
    }

    private val broken = mutableSetOf<Int>()

    /**
     * Called when the current song cannot be played (file gone, decoder error). Moves on to the next
     * playable song; repeat modes never retry a broken song. When nothing is playable, the alarm
     * falls back to the built-in tone.
     */
    fun markBroken(): NextSong {
        broken += current
        if (broken.size >= tracks.size) return NextSong.End(ResolvedEnd.BUILTIN_TONE)
        val keepsGoing = plan.repeatOne || plan.end == ResolvedEnd.RESTART_LIST || plan.end == ResolvedEnd.REPEAT_LAST
        repeat(tracks.size * 2 + 2) {
            if (position + 1 < order.size) {
                position++
            } else if (keepsGoing) {
                order = restartOrder()
                position = 0
            } else {
                return NextSong.End(plan.end)
            }
            if (current !in broken) return NextSong.Play(current)
        }
        return NextSong.End(ResolvedEnd.BUILTIN_TONE)
    }
}
