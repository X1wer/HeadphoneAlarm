package hu.ulpifex.fulesebreszto.alarm

import hu.ulpifex.fulesebreszto.data.AlarmStore
import hu.ulpifex.fulesebreszto.data.AppSettings
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.MissedRing
import hu.ulpifex.fulesebreszto.domain.NextTriggerCalculator
import hu.ulpifex.fulesebreszto.domain.ReschedulePlanner
import hu.ulpifex.fulesebreszto.domain.ScheduleOp
import hu.ulpifex.fulesebreszto.domain.TriggerKind
import hu.ulpifex.fulesebreszto.domain.TriggerValidator
import hu.ulpifex.fulesebreszto.domain.WeekDays
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.ZonedDateTime

/** Thin wrapper around AlarmManager so the engine can be tested on the JVM. */
interface AlarmClockGateway {
    fun arm(alarmId: Long, kind: TriggerKind, fireAt: Long, occurrence: Long)
    fun cancel(alarmId: Long, kind: TriggerKind)
}

/** User-facing notices the engine may raise (implemented with notifications). */
interface AlarmNotifier {
    fun missed(missed: MissedRing)
    fun snoozed(alarm: Alarm, until: Long)
    fun clearSnoozed(alarmId: Long)
}

/**
 * Single entry point for every change that affects scheduling: create, edit, toggle,
 * duplicate, delete, trigger bookkeeping, snooze, dismiss, reboot and clock changes.
 * All operations are serialised with a mutex so concurrent receivers cannot interleave.
 */
class AlarmEngine(
    private val store: AlarmStore,
    private val gateway: AlarmClockGateway,
    private val notifier: AlarmNotifier,
    private val clock: Clock,
    private val settings: suspend () -> AppSettings,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    private fun now(): ZonedDateTime = ZonedDateTime.now(clock)

    /** Creates or updates an alarm; returns its id and the time it will ring next (if any). */
    suspend fun save(alarm: Alarm): Pair<Long, Long?> = mutex.withLock {
        val now = now()
        val existing = if (alarm.id != 0L) store.get(alarm.id) else null
        var normalized = alarm.copy(
            label = alarm.label.trim().ifEmpty { Alarm.DEFAULT_LABEL },
            volumePercent = alarm.volumePercent.coerceIn(0, 100),
            snoozeMinutes = alarm.snoozeMinutes.coerceIn(Alarm.MIN_SNOOZE, Alarm.MAX_SNOOZE),
            maxRingMinutes = alarm.maxRingMinutes.coerceIn(Alarm.MIN_RING, Alarm.MAX_RING),
            oneShotDate = if (alarm.days.isEmpty) {
                alarm.oneShotDate ?: NextTriggerCalculator.defaultOneShotDate(alarm.hour, alarm.minute, now)
            } else {
                null
            },
            // An edit restarts scheduling and cancels a pending snooze.
            nextTriggerAt = null,
            snoozeUntil = null,
            lastTriggeredAt = existing?.lastTriggeredAt,
            createdAt = existing?.createdAt ?: now.toInstant().toEpochMilli(),
        )
        val id = store.save(normalized)
        normalized = normalized.copy(id = id)
        if (existing?.snoozeUntil != null) notifier.clearSnoozed(id)
        val planned = applyPlan(normalized, now)
        log("alarm $id saved (enabled=${planned.enabled})")
        id to (if (planned.enabled) planned.nextTriggerAt else null)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Long? = mutex.withLock {
        val alarm = store.get(id) ?: return@withLock null
        val now = now()
        var changed = alarm.copy(enabled = enabled, nextTriggerAt = null, snoozeUntil = null)
        if (enabled && alarm.days.isEmpty) {
            val date = alarm.oneShotDate
            val candidate = date?.let { NextTriggerCalculator.next(changed.copy(lastTriggeredAt = null), now) }
            if (date == null || candidate == null) {
                changed = changed.copy(oneShotDate = NextTriggerCalculator.defaultOneShotDate(alarm.hour, alarm.minute, now))
            }
        }
        if (alarm.snoozeUntil != null) notifier.clearSnoozed(id)
        val planned = applyPlan(changed, now)
        log("alarm $id enabled=$enabled")
        if (planned.enabled) planned.nextTriggerAt else null
    }

    suspend fun delete(id: Long) = mutex.withLock {
        ReschedulePlanner.planDeleted(id).forEach(::execute)
        store.delete(id)
        notifier.clearSnoozed(id)
        log("alarm $id deleted")
    }

    suspend fun duplicate(id: Long): Long? {
        val source = mutex.withLock { store.get(id) } ?: return null
        val copy = source.copy(
            id = 0,
            label = "${source.label} (másolat)",
            enabled = false,
            nextTriggerAt = null,
            lastTriggeredAt = null,
            snoozeUntil = null,
        )
        return save(copy).first
    }

    /** Rebuilds every AlarmManager entry from the database (boot, time/zone change, update). */
    suspend fun rescheduleAll(reason: String) = mutex.withLock {
        val now = now()
        val alarms = store.all()
        alarms.forEach { applyPlan(it, now) }
        log("rescheduled ${alarms.size} alarm(s): $reason")
    }

    /**
     * Called when a trigger fires. Returns the alarm to ring, or null if the trigger is stale
     * (deleted, disabled, edited, or this occurrence already rang).
     */
    suspend fun onTrigger(id: Long, kind: TriggerKind, occurrence: Long): Alarm? = mutex.withLock {
        if (id == TEST_ALARM_ID) return@withLock testAlarm(settings())
        val alarm = store.get(id)
        if (!TriggerValidator.isValid(alarm, kind, occurrence)) {
            log("ignored stale $kind trigger for $id")
            return@withLock null
        }
        val marked = TriggerValidator.markTriggered(alarm!!, kind, occurrence)
        if (kind == TriggerKind.SNOOZE) notifier.clearSnoozed(id)
        // The next repetition is scheduled right away, independent of how this ring ends.
        applyPlan(marked, now())
        log("trigger $kind for $id accepted")
        marked
    }

    /** Snoozes every alarm in [ids] (several alarms may share one ringing session). */
    suspend fun snooze(ids: List<Long>, minutes: Int): Long = mutex.withLock {
        val now = now()
        val until = now.toInstant().toEpochMilli() + minutes.coerceIn(Alarm.MIN_SNOOZE, Alarm.MAX_SNOOZE) * 60_000L
        ids.forEach { id ->
            if (id == TEST_ALARM_ID) {
                gateway.arm(TEST_ALARM_ID, TriggerKind.MAIN, until, until)
                return@forEach
            }
            val alarm = store.get(id) ?: return@forEach
            val snoozed = applyPlan(alarm.copy(snoozeUntil = until), now)
            notifier.snoozed(snoozed, until)
        }
        log("snoozed ${ids.size} alarm(s) for $minutes min")
        until
    }

    suspend fun dismiss(ids: List<Long>) = mutex.withLock {
        val now = now()
        ids.filter { it != TEST_ALARM_ID }.forEach { id ->
            val alarm = store.get(id) ?: return@forEach
            applyPlan(alarm.copy(snoozeUntil = null), now)
            notifier.clearSnoozed(id)
        }
        log("dismissed ${ids.size} alarm(s)")
    }

    suspend fun cancelSnooze(id: Long) = dismiss(listOf(id))

    suspend fun scheduleTest(minutes: Int): Long {
        val at = clock.millis() + minutes * 60_000L
        gateway.arm(TEST_ALARM_ID, TriggerKind.MAIN, at, at)
        log("test alarm in $minutes min")
        return at
    }

    /** Creates the first-run default alarm (weekdays 06:30, disabled) when the table is empty. */
    suspend fun ensureFirstRunAlarm(isEmpty: Boolean, defaults: AppSettings) {
        if (!isEmpty) return
        val alarm = Alarm.firstRunDefault(clock.millis()).copy(
            volumePercent = defaults.defaultVolume,
            snoozeMinutes = defaults.defaultSnoozeMinutes,
            maxRingMinutes = defaults.defaultMaxRingMinutes,
            vibrateWithHeadphones = defaults.defaultVibrateWithHeadphones,
        )
        save(alarm)
    }

    private suspend fun applyPlan(alarm: Alarm, now: ZonedDateTime): Alarm {
        val plan = ReschedulePlanner.plan(alarm, now)
        // Always persist: callers pass in state changes (snooze, trigger bookkeeping) that
        // are not in the store yet, even when the planner itself changes nothing.
        store.save(plan.updated)
        plan.ops.forEach(::execute)
        plan.missed.forEach {
            log("missed ${if (it.isSnooze) "snooze" else "occurrence"} for ${alarm.id}")
            notifier.missed(it)
        }
        return plan.updated
    }

    private fun execute(op: ScheduleOp) {
        when (op) {
            is ScheduleOp.Arm -> gateway.arm(op.alarmId, op.kind, op.fireAt, op.occurrence)
            is ScheduleOp.Cancel -> gateway.cancel(op.alarmId, op.kind)
        }
    }

    companion object {
        const val TEST_ALARM_ID = -1L

        fun testAlarm(settings: AppSettings) = Alarm(
            id = TEST_ALARM_ID,
            label = "Próbaébresztés",
            days = WeekDays.NONE,
            enabled = true,
            sound = BuiltInSounds.DEFAULT.ref,
            soundName = BuiltInSounds.DEFAULT.displayName,
            volumePercent = settings.defaultVolume,
            vibrateWithHeadphones = settings.defaultVibrateWithHeadphones,
            snoozeMinutes = settings.defaultSnoozeMinutes,
            maxRingMinutes = settings.defaultMaxRingMinutes,
        )
    }
}
