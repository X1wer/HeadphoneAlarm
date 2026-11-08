package hu.ulpifex.fulesebreszto.data

import hu.ulpifex.fulesebreszto.audio.OutputDevice
import hu.ulpifex.fulesebreszto.audio.OutputKind
import hu.ulpifex.fulesebreszto.data.db.AlarmDao
import hu.ulpifex.fulesebreszto.data.db.AlarmEntity
import hu.ulpifex.fulesebreszto.data.db.CustomSoundDao
import hu.ulpifex.fulesebreszto.data.db.CustomSoundEntity
import hu.ulpifex.fulesebreszto.data.db.KnownDeviceDao
import hu.ulpifex.fulesebreszto.data.db.KnownDeviceEntity
import hu.ulpifex.fulesebreszto.domain.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Storage abstraction used by the alarm engine (faked in unit tests). */
interface AlarmStore {
    suspend fun all(): List<Alarm>
    suspend fun get(id: Long): Alarm?
    suspend fun save(alarm: Alarm): Long
    suspend fun delete(id: Long)
}

class AlarmRepository(private val dao: AlarmDao) : AlarmStore {
    val alarms: Flow<List<Alarm>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Alarm?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun all(): List<Alarm> = dao.getAll().map { it.toDomain() }
    override suspend fun get(id: Long): Alarm? = dao.get(id)?.toDomain()

    override suspend fun save(alarm: Alarm): Long {
        val entity = AlarmEntity.from(alarm)
        return if (alarm.id == 0L) dao.insert(entity) else {
            dao.update(entity)
            alarm.id
        }
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    suspend fun count(): Int = dao.count()
    suspend fun countUsingSound(ref: String): Int = dao.countUsingSound(ref)
    suspend fun replaceSound(oldRef: String, newRef: String, newName: String) = dao.replaceSound(oldRef, newRef, newName)
}

data class CustomSound(val uri: String, val displayName: String)

class CustomSoundRepository(private val dao: CustomSoundDao) {
    val sounds: Flow<List<CustomSound>> =
        dao.observeAll().map { list -> list.map { CustomSound(it.uri, it.displayName) } }

    suspend fun add(uri: String, displayName: String, now: Long) {
        if (dao.findByUri(uri) == null) dao.insert(CustomSoundEntity(uri = uri, displayName = displayName, addedAt = now))
    }

    suspend fun remove(uri: String) = dao.deleteByUri(uri)
}

data class KnownDevice(
    val key: String,
    val name: String,
    val kind: OutputKind,
    val allowed: Boolean,
    val lastSeenAt: Long,
)

class DeviceRepository(private val dao: KnownDeviceDao) {
    val devices: Flow<List<KnownDevice>> = dao.observeAll().map { list ->
        list.map {
            KnownDevice(
                key = it.key,
                name = it.name,
                kind = OutputKind.entries.firstOrNull { k -> k.name == it.kind } ?: OutputKind.OTHER,
                allowed = it.allowed,
                lastSeenAt = it.lastSeenAt,
            )
        }
    }

    val allowedKeys: Flow<Set<String>> = dao.observeAllowedKeys().map { it.toSet() }

    suspend fun allowedKeysNow(): Set<String> = dao.allowedKeys().toSet()

    /** Records approval-requiring outputs (Bluetooth, USB) so the user can allow them later. */
    suspend fun remember(devices: List<OutputDevice>, now: Long) {
        devices.filter { it.kind.needsApproval || it.kind == OutputKind.BLE_SPEAKER }.forEach { d ->
            val existing = dao.get(d.key)
            dao.upsert(
                KnownDeviceEntity(
                    key = d.key,
                    name = d.name,
                    kind = d.kind.name,
                    allowed = existing?.allowed ?: false,
                    lastSeenAt = now,
                ),
            )
        }
    }

    suspend fun setAllowed(key: String, allowed: Boolean) = dao.setAllowed(key, allowed)
    suspend fun forget(key: String) = dao.delete(key)
}
