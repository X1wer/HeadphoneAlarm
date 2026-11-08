package hu.ulpifex.fulesebreszto

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.NextTriggerCalculator
import hu.ulpifex.fulesebreszto.domain.WeekDays
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

/** Exercises the real AlarmManager through the app's engine (setAlarmClock). */
@RunWith(AndroidJUnit4::class)
class AlarmSchedulingDeviceTest {
    private val app = ApplicationProvider.getApplicationContext<FulesApp>()
    private val container = app.container
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val created = mutableListOf<Long>()

    @After
    fun cleanUp() = runBlocking {
        created.forEach { container.engine.delete(it) }
    }

    @Test
    fun exactAlarmPermissionIsAvailable() {
        assertTrue(container.alarmClock.canScheduleExact())
    }

    @Test
    fun savedAlarmBecomesTheSystemAlarmClock() = runBlocking {
        // Disable every other alarm's influence by picking a time within the next few minutes.
        val now = ZonedDateTime.now()
        val target = now.plusMinutes(3)
        val (id, next) = container.engine.save(
            Alarm(label = "Eszközteszt", hour = target.hour, minute = target.minute, days = WeekDays.EVERY_DAY, enabled = true),
        )
        created += id
        val expected = NextTriggerCalculator.nextMillis(container.alarms.get(id)!!, now)
        assertEquals(expected, next)
        val info = alarmManager.nextAlarmClock
        assertNotNull(info)
        assertEquals(next, info!!.triggerTime)
    }

    @Test
    fun deletingRemovesTheSystemAlarmClock() = runBlocking {
        val target = ZonedDateTime.now().plusMinutes(2)
        val (id, next) = container.engine.save(
            Alarm(label = "Törlendő", hour = target.hour, minute = target.minute, days = WeekDays.EVERY_DAY, enabled = true),
        )
        assertEquals(next, alarmManager.nextAlarmClock?.triggerTime)
        container.engine.delete(id)
        val after = alarmManager.nextAlarmClock?.triggerTime
        assertTrue("deleted alarm must not stay scheduled", after == null || after != next)
    }
}
