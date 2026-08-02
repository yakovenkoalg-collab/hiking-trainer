package ru.yakovenko.mountainform.domain

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class WorkoutTimerMode {
    NONE,
    SET,
    REST,
}

@Serializable
data class WorkoutExecutionState(
    val sessionId: String,
    val workoutStarted: Boolean = false,
    val paused: Boolean = false,
    val targetIndex: Int = 0,
    val workoutElapsedSeconds: Int = 0,
    val workoutTickStartedAtEpochMillis: Long? = null,
    val timerMode: WorkoutTimerMode = WorkoutTimerMode.NONE,
    val timerTickStartedAtEpochMillis: Long? = null,
    val setElapsedSeconds: Int = 0,
    val workRemainingSeconds: Int = 0,
    val restRemainingSeconds: Int = 0,
    val restElapsedSeconds: Int = 0,
    val restPlannedSeconds: Int = 0,
    val restSourceTargetIndex: Int? = null,
    val restAlerted: Boolean = false,
) {
    fun workoutElapsedAt(nowEpochMillis: Long): Int = workoutElapsedSeconds +
        if (workoutStarted && !paused) elapsedSince(workoutTickStartedAtEpochMillis, nowEpochMillis) else 0

    fun setElapsedAt(nowEpochMillis: Long): Int = setElapsedSeconds +
        if (!paused && timerMode == WorkoutTimerMode.SET) elapsedSince(timerTickStartedAtEpochMillis, nowEpochMillis) else 0

    fun workRemainingAt(nowEpochMillis: Long): Int = max(
        0,
        workRemainingSeconds -
            if (!paused && timerMode == WorkoutTimerMode.SET) elapsedSince(timerTickStartedAtEpochMillis, nowEpochMillis) else 0,
    )

    fun restRemainingAt(nowEpochMillis: Long): Int = max(
        0,
        restRemainingSeconds -
            if (!paused && timerMode == WorkoutTimerMode.REST) elapsedSince(timerTickStartedAtEpochMillis, nowEpochMillis) else 0,
    )

    fun restElapsedAt(nowEpochMillis: Long): Int = restElapsedSeconds +
        if (!paused && timerMode == WorkoutTimerMode.REST) elapsedSince(timerTickStartedAtEpochMillis, nowEpochMillis) else 0

    fun restOvertimeAt(nowEpochMillis: Long): Int = max(0, restElapsedAt(nowEpochMillis) - restPlannedSeconds)

    fun snapshotAt(nowEpochMillis: Long): WorkoutExecutionState = copy(
        workoutElapsedSeconds = workoutElapsedAt(nowEpochMillis),
        workoutTickStartedAtEpochMillis = if (workoutStarted && !paused) nowEpochMillis else null,
        setElapsedSeconds = setElapsedAt(nowEpochMillis),
        workRemainingSeconds = workRemainingAt(nowEpochMillis),
        restRemainingSeconds = restRemainingAt(nowEpochMillis),
        restElapsedSeconds = restElapsedAt(nowEpochMillis),
        timerTickStartedAtEpochMillis = if (!paused && timerMode != WorkoutTimerMode.NONE) nowEpochMillis else null,
    )

    private fun elapsedSince(startedAtEpochMillis: Long?, nowEpochMillis: Long): Int =
        startedAtEpochMillis?.let { max(0L, nowEpochMillis - it).div(1_000L).toInt() } ?: 0
}
