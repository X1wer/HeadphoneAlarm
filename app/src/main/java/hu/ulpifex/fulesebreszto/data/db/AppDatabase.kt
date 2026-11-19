package hu.ulpifex.fulesebreszto.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, CustomSoundEntity::class, KnownDeviceEntity::class],
    version = 2,
    exportSchema = true,
    // 1 -> 2 (1.1.0): output mode, colour and playlist options of an alarm; existing rows get the defaults.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun customSoundDao(): CustomSoundDao
    abstract fun knownDeviceDao(): KnownDeviceDao

    companion object {
        const val NAME = "fules_ebreszto.db"

        /**
         * [storageContext] should be a device-protected storage context so that alarms can be
         * rescheduled and rung after a reboot even before the user unlocks the phone.
         */
        fun build(storageContext: Context): AppDatabase =
            Room.databaseBuilder(storageContext, AppDatabase::class.java, NAME).build()
    }
}
