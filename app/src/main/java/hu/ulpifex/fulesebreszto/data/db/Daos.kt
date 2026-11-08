package hu.ulpifex.fulesebreszto.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observe(id: Long): Flow<AlarmEntity?>

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT COUNT(*) FROM alarms")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM alarms WHERE sound_ref = :soundRef")
    suspend fun countUsingSound(soundRef: String): Int

    @Query("UPDATE alarms SET sound_ref = :newRef, sound_name = :newName WHERE sound_ref = :oldRef")
    suspend fun replaceSound(oldRef: String, newRef: String, newName: String): Int
}

@Dao
interface CustomSoundDao {
    @Query("SELECT * FROM custom_sounds ORDER BY added_at DESC")
    fun observeAll(): Flow<List<CustomSoundEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sound: CustomSoundEntity): Long

    @Query("SELECT * FROM custom_sounds WHERE uri = :uri")
    suspend fun findByUri(uri: String): CustomSoundEntity?

    @Query("DELETE FROM custom_sounds WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}

@Dao
interface KnownDeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY allowed DESC, name")
    fun observeAll(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT `key` FROM known_devices WHERE allowed = 1")
    suspend fun allowedKeys(): List<String>

    @Query("SELECT `key` FROM known_devices WHERE allowed = 1")
    fun observeAllowedKeys(): Flow<List<String>>

    @Query("SELECT * FROM known_devices WHERE `key` = :key")
    suspend fun get(key: String): KnownDeviceEntity?

    @Upsert
    suspend fun upsert(device: KnownDeviceEntity)

    @Query("UPDATE known_devices SET allowed = :allowed WHERE `key` = :key")
    suspend fun setAllowed(key: String, allowed: Boolean)

    @Query("DELETE FROM known_devices WHERE `key` = :key")
    suspend fun delete(key: String)
}
