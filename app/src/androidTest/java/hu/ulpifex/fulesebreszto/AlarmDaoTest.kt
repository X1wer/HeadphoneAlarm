package hu.ulpifex.fulesebreszto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.ulpifex.fulesebreszto.data.AlarmRepository
import hu.ulpifex.fulesebreszto.data.db.AppDatabase
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.WeekDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AlarmRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = AlarmRepository(db.alarmDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun roundTripKeepsEveryField() = runTest {
        val alarm = Alarm(
            label = "Munkahelyi ébresztés", hour = 6, minute = 30, days = WeekDays.WORKDAYS,
            oneShotDate = LocalDate.of(2026, 11, 20), enabled = true,
            sound = SoundRef.Custom("content://x/y"), soundName = "Saját", volumePercent = 45,
            vibrateWithHeadphones = true, snoozeMinutes = 7, maxRingMinutes = 12,
            nextTriggerAt = 1, lastTriggeredAt = 2, snoozeUntil = 3, createdAt = 4,
        )
        val id = repo.save(alarm)
        assertEquals(alarm.copy(id = id), repo.get(id))
    }

    @Test
    fun listIsOrderedAndObservable() = runTest {
        repo.save(Alarm(hour = 8, minute = 0))
        repo.save(Alarm(hour = 6, minute = 30))
        repo.save(Alarm(hour = 6, minute = 5))
        assertEquals(listOf(6 to 5, 6 to 30, 8 to 0), repo.alarms.first().map { it.hour to it.minute })
    }

    @Test
    fun updateAndDelete() = runTest {
        val id = repo.save(Alarm(hour = 6, minute = 30))
        repo.save(repo.get(id)!!.copy(hour = 7))
        assertEquals(7, repo.get(id)!!.hour)
        repo.delete(id)
        assertNull(repo.get(id))
    }

    @Test
    fun removedCustomSoundFallsBackToBuiltIn() = runTest {
        val id = repo.save(Alarm(sound = SoundRef.Custom("content://gone"), soundName = "Régi"))
        repo.replaceSound("content://gone", BuiltInSounds.DEFAULT.ref.serialized, BuiltInSounds.DEFAULT.displayName)
        assertEquals(BuiltInSounds.DEFAULT.ref, repo.get(id)!!.sound)
        assertEquals(BuiltInSounds.DEFAULT.displayName, repo.get(id)!!.soundName)
    }
}
