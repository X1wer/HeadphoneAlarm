package hu.ulpifex.fulesebreszto

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hu.ulpifex.fulesebreszto.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Alarms saved by 1.0.x (schema 1) must survive the update to schema 2 with the new defaults. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsAlarms() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms (id, label, hour, minute, days_mask, one_shot_date, enabled, sound_ref, sound_name,
                    volume_percent, vibrate_with_headphones, snooze_minutes, max_ring_minutes, next_trigger_at,
                    last_triggered_at, snooze_until, created_at)
                VALUES (7, 'Munkahelyi ébresztés', 6, 30, 31, NULL, 1, 'builtin:soft', 'Lágy dallam',
                    45, 0, 10, 10, 1000, NULL, NULL, 5)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT label, hour, minute, enabled, output, color_argb, playlist_order, playlist_end, playlist_start_track_id FROM alarms WHERE id = 7").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Munkahelyi ébresztés", c.getString(0))
                assertEquals(6, c.getInt(1))
                assertEquals(30, c.getInt(2))
                assertEquals(1, c.getInt(3))
                assertEquals("HEADPHONES", c.getString(4))
                assertTrue(c.isNull(5))
                assertEquals("FOLLOW_APP", c.getString(6))
                assertEquals("FOLLOW_APP", c.getString(7))
                assertTrue(c.isNull(8))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
