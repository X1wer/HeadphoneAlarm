package hu.ulpifex.fulesebreszto.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.domain.WeekDays
import java.time.LocalDate

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    /** ISO-8601 date (yyyy-MM-dd) for one-shot alarms. */
    @ColumnInfo(name = "one_shot_date") val oneShotDate: String?,
    val enabled: Boolean,
    @ColumnInfo(name = "sound_ref") val soundRef: String,
    @ColumnInfo(name = "sound_name") val soundName: String,
    @ColumnInfo(name = "volume_percent") val volumePercent: Int,
    @ColumnInfo(name = "vibrate_with_headphones") val vibrateWithHeadphones: Boolean,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int,
    @ColumnInfo(name = "max_ring_minutes") val maxRingMinutes: Int,
    @ColumnInfo(name = "next_trigger_at") val nextTriggerAt: Long?,
    @ColumnInfo(name = "last_triggered_at") val lastTriggeredAt: Long?,
    @ColumnInfo(name = "snooze_until") val snoozeUntil: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    fun toDomain() = Alarm(
        id = id,
        label = label,
        hour = hour,
        minute = minute,
        days = WeekDays(daysMask and 0x7F),
        oneShotDate = oneShotDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        enabled = enabled,
        sound = SoundRef.parse(soundRef),
        soundName = soundName,
        volumePercent = volumePercent,
        vibrateWithHeadphones = vibrateWithHeadphones,
        snoozeMinutes = snoozeMinutes,
        maxRingMinutes = maxRingMinutes,
        nextTriggerAt = nextTriggerAt,
        lastTriggeredAt = lastTriggeredAt,
        snoozeUntil = snoozeUntil,
        createdAt = createdAt,
    )

    companion object {
        fun from(a: Alarm) = AlarmEntity(
            id = a.id,
            label = a.label,
            hour = a.hour,
            minute = a.minute,
            daysMask = a.days.mask,
            oneShotDate = a.oneShotDate?.toString(),
            enabled = a.enabled,
            soundRef = a.sound.serialized,
            soundName = a.soundName,
            volumePercent = a.volumePercent,
            vibrateWithHeadphones = a.vibrateWithHeadphones,
            snoozeMinutes = a.snoozeMinutes,
            maxRingMinutes = a.maxRingMinutes,
            nextTriggerAt = a.nextTriggerAt,
            lastTriggeredAt = a.lastTriggeredAt,
            snoozeUntil = a.snoozeUntil,
            createdAt = a.createdAt,
        )
    }
}

@Entity(tableName = "custom_sounds", indices = [Index(value = ["uri"], unique = true)])
data class CustomSoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Bluetooth / USB outputs seen by the app. Only rows with [allowed] = true may play alarms. */
@Entity(tableName = "known_devices")
data class KnownDeviceEntity(
    @PrimaryKey val key: String,
    val name: String,
    val kind: String,
    val allowed: Boolean,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)
