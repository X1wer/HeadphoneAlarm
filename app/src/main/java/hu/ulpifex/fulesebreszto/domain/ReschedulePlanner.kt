package hu.ulpifex.fulesebreszto.domain

import java.time.ZonedDateTime

enum class TriggerKind { MAIN, SNOOZE }

sealed interface ScheduleOp {
    val alarmId: Long
    val kind: TriggerKind

    /** Arm an exact alarm-clock trigger at [fireAt] for the occurrence [occurrence]. */
    data class Arm(
        override val alarmId: Long,
        override val kind: TriggerKind,
        val fireAt: Long,
        val occurrence: Long,
    ) : ScheduleOp

    data class Cancel(override val alarmId: Long, override val kind: TriggerKind) : ScheduleOp
}

/** A ring that could not happen (device off, clock jumped) and is reported to the user. */
data class MissedRing(val alarm: Alarm, val occurrence: Long, val isSnooze: Boolean)

data class AlarmPlan(
    val updated: Alarm,
    val ops: List<ScheduleOp>,
    val missed: List<MissedRing>,
)

/**
 * Turns the persisted state of an alarm into AlarmManager operations. Pure, so boot,
 * time-change, edit, toggle and delete handling are all unit-tested through it.
 */
object ReschedulePlanner {

    /** Delay used to ring a slightly late occurrence "now" after a reboot or clock jump. */
    const val LATE_RING_DELAY_MS = 3_000L

    fun plan(alarm: Alarm, now: ZonedDateTime): AlarmPlan {
        val nowMs = now.toInstant().toEpochMilli()
        val graceMs = alarm.maxRingMinutes.coerceIn(Alarm.MIN_RING, Alarm.MAX_RING) * 60_000L
        val ops = mutableListOf<ScheduleOp>()
        val missed = mutableListOf<MissedRing>()
        var updated = alarm

        if (!alarm.enabled) {
            ops += ScheduleOp.Cancel(alarm.id, TriggerKind.MAIN)
            updated = updated.copy(nextTriggerAt = null)
        } else {
            val stored = alarm.nextTriggerAt
            val storedIsPending = stored != null && stored <= nowMs && stored != alarm.lastTriggeredAt
            if (storedIsPending && nowMs - stored <= graceMs) {
                // Missed by less than the ring window: ring as soon as possible.
                ops += ScheduleOp.Arm(alarm.id, TriggerKind.MAIN, nowMs + LATE_RING_DELAY_MS, stored)
            } else {
                if (storedIsPending) missed += MissedRing(alarm, stored, isSnooze = false)
                val base = if (storedIsPending) alarm.copy(lastTriggeredAt = stored) else alarm
                val next = NextTriggerCalculator.nextMillis(base, now)
                if (next == null) {
                    updated = updated.copy(enabled = false, nextTriggerAt = null)
                    ops += ScheduleOp.Cancel(alarm.id, TriggerKind.MAIN)
                } else {
                    updated = updated.copy(nextTriggerAt = next, lastTriggeredAt = base.lastTriggeredAt)
                    ops += ScheduleOp.Arm(alarm.id, TriggerKind.MAIN, next, next)
                }
            }
        }

        val snooze = alarm.snoozeUntil
        when {
            snooze == null -> ops += ScheduleOp.Cancel(alarm.id, TriggerKind.SNOOZE)
            snooze > nowMs -> ops += ScheduleOp.Arm(alarm.id, TriggerKind.SNOOZE, snooze, snooze)
            nowMs - snooze <= graceMs ->
                ops += ScheduleOp.Arm(alarm.id, TriggerKind.SNOOZE, nowMs + LATE_RING_DELAY_MS, snooze)
            else -> {
                missed += MissedRing(alarm, snooze, isSnooze = true)
                updated = updated.copy(snoozeUntil = null)
                ops += ScheduleOp.Cancel(alarm.id, TriggerKind.SNOOZE)
            }
        }
        return AlarmPlan(updated, ops, missed)
    }

    fun planDeleted(alarmId: Long): List<ScheduleOp> = listOf(
        ScheduleOp.Cancel(alarmId, TriggerKind.MAIN),
        ScheduleOp.Cancel(alarmId, TriggerKind.SNOOZE),
    )
}

/** Validation of an incoming trigger against the current persisted alarm state. */
object TriggerValidator {
    fun isValid(alarm: Alarm?, kind: TriggerKind, occurrence: Long): Boolean {
        if (alarm == null) return false // deleted alarms never ring
        return when (kind) {
            TriggerKind.MAIN ->
                alarm.enabled && alarm.nextTriggerAt == occurrence && alarm.lastTriggeredAt != occurrence
            TriggerKind.SNOOZE -> alarm.snoozeUntil == occurrence
        }
    }

    /**
     * Bookkeeping applied the moment a main occurrence starts ringing: it is recorded as
     * handled, and a one-shot alarm is switched off so it can never ring again.
     */
    fun markTriggered(alarm: Alarm, kind: TriggerKind, occurrence: Long): Alarm = when (kind) {
        TriggerKind.MAIN -> alarm.copy(
            lastTriggeredAt = occurrence,
            enabled = alarm.enabled && alarm.isRepeating,
            nextTriggerAt = if (alarm.isRepeating) alarm.nextTriggerAt else null,
        )
        TriggerKind.SNOOZE -> alarm.copy(snoozeUntil = null)
    }
}
