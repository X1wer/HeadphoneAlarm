package hu.ulpifex.fulesebreszto.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaylistSequenceTest {

    private val songs = listOf(10L, 20L, 30L, 40L)

    private fun plan(shuffle: Boolean = false, end: ResolvedEnd = ResolvedEnd.RESTART_LIST, one: Boolean = false, first: Boolean = false) =
        PlaylistPlan(shuffle, repeatOne = one, stopAfterFirst = first, end = end)

    /** Plays [steps] songs and returns their track ids, or the end action. */
    private fun play(seq: PlaylistSequence, steps: Int): List<Any> {
        val out = mutableListOf<Any>(songs[seq.current])
        repeat(steps - 1) {
            when (val n = seq.next()) {
                is NextSong.Play -> out += songs[n.index]
                is NextSong.End -> { out += n.end; return out }
            }
        }
        return out
    }

    @Test
    fun `follow app uses Zengő's saved shuffle and repeat`() {
        val follow = PlaylistOptions()
        assertEquals(plan(shuffle = true), PlaylistPlanner.resolve(follow, ZengoMode(shuffle = true, repeat = "ALL")))
        assertEquals(plan(one = true), PlaylistPlanner.resolve(follow, ZengoMode(false, "ONE")))
        assertEquals(plan(end = ResolvedEnd.VIBRATE_ONLY), PlaylistPlanner.resolve(follow, ZengoMode(false, "OFF")))
        assertEquals(
            plan(end = ResolvedEnd.VIBRATE_ONLY, first = true),
            PlaylistPlanner.resolve(follow, ZengoMode(false, "STOP_AFTER_CURRENT")),
        )
        // Zengő not reachable: in order, restarting.
        assertEquals(plan(), PlaylistPlanner.resolve(follow, null))
    }

    @Test
    fun `explicit choices override Zengő`() {
        val options = PlaylistOptions(order = PlaylistOrder.IN_ORDER, end = PlaylistEnd.BUILTIN_TONE)
        assertEquals(plan(end = ResolvedEnd.BUILTIN_TONE), PlaylistPlanner.resolve(options, ZengoMode(true, "ONE")))
        val shuffled = PlaylistOptions(order = PlaylistOrder.SHUFFLE, end = PlaylistEnd.STOP_ALARM)
        assertEquals(plan(shuffle = true, end = ResolvedEnd.STOP_ALARM), PlaylistPlanner.resolve(shuffled, ZengoMode(false, "ALL")))
    }

    @Test
    fun `in order from a chosen start song, then the end action`() {
        assertEquals(listOf(30L, 40L, 10L, 20L, 30L), play(PlaylistSequence(songs, plan(), startTrackId = 30), 5))
        assertEquals(listOf(30L, 40L, 40L, 40L), play(PlaylistSequence(songs, plan(end = ResolvedEnd.REPEAT_LAST), 30), 4))
        assertEquals(listOf(10L, 20L, 30L, 40L, ResolvedEnd.BUILTIN_TONE), play(PlaylistSequence(songs, plan(end = ResolvedEnd.BUILTIN_TONE), null), 6))
        assertEquals(listOf(40L, ResolvedEnd.STOP_ALARM), play(PlaylistSequence(songs, plan(end = ResolvedEnd.STOP_ALARM), 40), 3))
    }

    @Test
    fun `unknown start song starts at the top`() {
        assertEquals(10L, songs[PlaylistSequence(songs, plan(), startTrackId = 999).current])
    }

    @Test
    fun `repeat one and stop after first`() {
        assertEquals(listOf(20L, 20L, 20L), play(PlaylistSequence(songs, plan(one = true), 20), 3))
        assertEquals(listOf(20L, ResolvedEnd.VIBRATE_ONLY), play(PlaylistSequence(songs, plan(first = true, end = ResolvedEnd.VIBRATE_ONLY), 20), 3))
    }

    @Test
    fun `shuffle starts with the chosen song and plays every song once per round`() {
        repeat(20) { seed ->
            val seq = PlaylistSequence(songs, plan(shuffle = true), startTrackId = 30, random = Random(seed))
            val round = play(seq, 4)
            assertEquals(30L, round.first())
            assertEquals(songs.toSet(), round.toSet())
            // Across the reshuffle the same song never plays twice in a row.
            val next = (seq.next() as NextSong.Play).index
            assertNotEquals(round.last(), songs[next])
        }
    }

    @Test
    fun `shuffle without a start song begins anywhere`() {
        val firsts = (0 until 40).map { songs[PlaylistSequence(songs, plan(shuffle = true), null, Random(it)).current] }.toSet()
        assertTrue(firsts.size > 1)
    }

    @Test
    fun `broken songs are skipped and never retried`() {
        val seq = PlaylistSequence(songs, plan(one = true), startTrackId = 10)
        assertEquals(NextSong.Play(1), seq.markBroken()) // repeat-one moves on from a broken song
        assertEquals(NextSong.Play(1), seq.next())

        val ends = PlaylistSequence(songs, plan(end = ResolvedEnd.VIBRATE_ONLY), startTrackId = 40)
        assertEquals(NextSong.End(ResolvedEnd.VIBRATE_ONLY), ends.markBroken())

        // A broken song is not tried again when the list restarts.
        val rounds = PlaylistSequence(songs, plan(), startTrackId = 10)
        assertEquals(NextSong.Play(1), rounds.next()) // 20
        assertEquals(NextSong.Play(2), rounds.markBroken()) // 20 broken → 30
        assertEquals(NextSong.Play(3), rounds.next()) // 40
        assertEquals(NextSong.Play(0), rounds.next()) // restart → 10
        assertEquals(NextSong.Play(2), rounds.next()) // 20 skipped → 30

        val all = PlaylistSequence(listOf(1L, 2L), plan(), null)
        assertEquals(NextSong.Play(1), all.markBroken())
        assertEquals(NextSong.End(ResolvedEnd.BUILTIN_TONE), all.markBroken())
    }

    @Test
    fun `zengő references survive serialisation`() {
        val ref = SoundRef.Zengo("io.github.x1wer.szabadzene.alarmplaylists", 12)
        assertEquals(ref, SoundRef.parse(ref.serialized))
        assertEquals(SoundRef.Zengo("a.b", 0), SoundRef.parse("zengo:a.b/0"))
        assertEquals(BuiltInSounds.DEFAULT.ref, SoundRef.parse("zengo:broken"))
        assertFalse(SoundRef.parse("content://x/y") is SoundRef.Zengo)
    }

    @Test
    fun `loud alarms default to red, headphone alarms to teal`() {
        assertEquals(AlarmColors.RED, Alarm(output = AlarmOutput.SPEAKER).displayColor)
        assertEquals(AlarmColors.TEAL, Alarm().displayColor)
        assertEquals(AlarmColors.GREEN, Alarm(output = AlarmOutput.SPEAKER, color = AlarmColors.GREEN).displayColor)
    }
}
