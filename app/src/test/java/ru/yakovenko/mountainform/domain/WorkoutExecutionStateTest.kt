package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutExecutionStateTest {
    @Test
    fun previewDoesNotAdvanceTimers() {
        val state = WorkoutExecutionState(sessionId = "session", workRemainingSeconds = 60)

        assertEquals(0, state.workoutElapsedAt(20_000))
        assertEquals(0, state.setElapsedAt(20_000))
        assertEquals(60, state.workRemainingAt(20_000))
    }

    @Test
    fun startedSetAdvancesWorkoutAndExerciseTimers() {
        val state = WorkoutExecutionState(
            sessionId = "session",
            workoutStarted = true,
            workoutTickStartedAtEpochMillis = 10_000,
            timerMode = WorkoutTimerMode.SET,
            timerTickStartedAtEpochMillis = 12_000,
            workRemainingSeconds = 30,
        )

        assertEquals(10, state.workoutElapsedAt(20_000))
        assertEquals(8, state.setElapsedAt(20_000))
        assertEquals(22, state.workRemainingAt(20_000))
    }

    @Test
    fun pausedStateDoesNotAdvance() {
        val state = WorkoutExecutionState(
            sessionId = "session",
            workoutStarted = true,
            paused = true,
            workoutElapsedSeconds = 45,
            timerMode = WorkoutTimerMode.REST,
            restRemainingSeconds = 60,
        )

        assertEquals(45, state.workoutElapsedAt(200_000))
        assertEquals(60, state.restRemainingAt(200_000))
    }

    @Test
    fun restKeepsCountingAfterPlannedTimeAndReportsOvertime() {
        val state = WorkoutExecutionState(
            sessionId = "session",
            workoutStarted = true,
            timerMode = WorkoutTimerMode.REST,
            timerTickStartedAtEpochMillis = 10_000,
            restRemainingSeconds = 30,
            restPlannedSeconds = 30,
        )

        assertEquals(0, state.restRemainingAt(45_000))
        assertEquals(35, state.restElapsedAt(45_000))
        assertEquals(5, state.restOvertimeAt(45_000))
    }

    @Test
    fun restSnapshotPersistsElapsedTime() {
        val state = WorkoutExecutionState(
            sessionId = "session",
            timerMode = WorkoutTimerMode.REST,
            timerTickStartedAtEpochMillis = 20_000,
            restRemainingSeconds = 60,
            restPlannedSeconds = 60,
        )

        val snapshot = state.snapshotAt(45_000)

        assertEquals(25, snapshot.restElapsedSeconds)
        assertEquals(35, snapshot.restRemainingSeconds)
        assertEquals(45_000L, snapshot.timerTickStartedAtEpochMillis)
    }

    @Test
    fun stalePersistedTimerIsPausedWithoutAddingWallClockTime() {
        val state = WorkoutExecutionState(
            sessionId = "session",
            workoutStarted = true,
            workoutElapsedSeconds = 900,
            workoutTickStartedAtEpochMillis = 1_000,
            timerMode = WorkoutTimerMode.SET,
            timerTickStartedAtEpochMillis = 1_000,
            setElapsedSeconds = 30,
        )

        val restored = state.restoreForForeground(nowEpochMillis = 4 * 60 * 60 * 1_000L)

        assertEquals(true, restored.paused)
        assertEquals(900, restored.workoutElapsedAt(99_000_000))
        assertEquals(WorkoutTimerMode.NONE, restored.timerMode)
    }

    @Test
    fun completionDurationFlagsMultiDayAndSuspiciouslyShortValues() {
        assertEquals(true, durationLooksImplausible(4 * 60 * 60, 65))
        assertEquals(true, durationLooksImplausible(27, 55))
        assertEquals(true, durationLooksImplausible(0, 55))
        assertEquals(true, durationLooksImplausible(5 * 60, 70))
        assertEquals(false, durationLooksImplausible(3_600, 65))
        assertEquals(false, durationLooksImplausible(3 * 60 * 60, 90))
    }
}
