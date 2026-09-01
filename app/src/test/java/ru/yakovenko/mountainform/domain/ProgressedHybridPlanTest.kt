package ru.yakovenko.mountainform.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ShoulderLoadPhase
import ru.yakovenko.mountainform.data.seedExerciseCatalog
import java.time.LocalDate

class ProgressedHybridPlanTest {
    @Test
    fun safeBlockHasCorrectedThursdayAndProgressedSecondWeek() {
        val today = LocalDate.of(2026, 8, 28)
        val plan = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = false,
            today = today,
            generatedAtEpochMillis = 1,
        )

        assertEquals(8, plan.sessions.size)
        assertEquals(
            listOf(55, 55, 70, 60, 60, 30, 75, 65),
            plan.sessions.map { it.durationMinutes },
        )
        assertEquals(LocalDate.of(2026, 8, 30).toEpochDay(), plan.replacePlannedFromEpochDay)
        assertEquals(LocalDate.of(2026, 9, 13).toEpochDay(), plan.replacePlannedThroughEpochDay)
        assertEquals(1, plan.sessions.count { it.title.startsWith("Опциональный") })
        assertEquals(1, plan.sessions.count { it.type == "STRENGTH" })
        assertTrue(plan.sessions.filter { it.type == "STRENGTH" }.all { it.targetRpe == 6 })
        val thursday = plan.sessions.single { it.plannedEpochDay == LocalDate.of(2026, 9, 3).toEpochDay() }
        assertEquals("HYBRID", thursday.type)
        assertEquals(5, thursday.targetRpe)
        assertEquals(5, thursday.steps.count { it.blockId == "home-circuit" })
        assertFalse(plan.sessions.any { it.plannedEpochDay == LocalDate.of(2026, 9, 4).toEpochDay() })
        assertTrue(plan.sessions.none { session -> session.steps.any { "SHOULDER_CLEARANCE_REQUIRED" in it.restrictionTags } })

        val catalogIds = seedExerciseCatalog(Json.Default).mapTo(mutableSetOf()) { it.id }
        assertTrue(plan.sessions.flatMap { it.steps }.all { it.exerciseId in catalogIds })
    }

    @Test
    fun clearedVersionAddsOnlyTaggedLowLoadUpperBodyMovements() {
        val plan = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = true,
            today = LocalDate.of(2026, 8, 28),
            generatedAtEpochMillis = 1,
        )
        val upperSteps = plan.sessions.flatMap { it.steps }
            .filter { "SHOULDER_CLEARANCE_REQUIRED" in it.restrictionTags }

        assertEquals(3, upperSteps.size)
        assertTrue(upperSteps.all { ShoulderSafety.access(it, ShoulderLoadPhase.THERAPIST_CLEARED) == ShoulderExerciseAccess.ALLOWED })
        assertTrue(upperSteps.none { it.title.contains("отжим", ignoreCase = true) || it.title.contains("брусь", ignoreCase = true) })
        val firstStrengthUpperOrder = WorkoutPlanCompiler.compile(plan.sessions.first { it.type == "STRENGTH" }.steps)
            .filter { it.blockId.startsWith("upper") }
            .map { it.step.id }
        assertEquals(
            listOf("supported-row", "supported-row", "biceps", "triceps", "biceps", "triceps"),
            firstStrengthUpperOrder,
        )
    }

    @Test
    fun proposalCanRefreshFutureStrengthAfterClearanceWithoutRecreatingPastSessions() {
        val beforeClearance = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = false,
            today = LocalDate.of(2026, 8, 28),
            generatedAtEpochMillis = 1,
        )
        val safeSessions = beforeClearance.sessions.associate { it.id to it.plannedEpochDay }

        assertFalse(
            ProgressedHybridPlan.isRelevant(
                LocalDate.of(2026, 8, 28), safeSessions, includeClearedUpperBody = false,
            ),
        )
        assertTrue(
            ProgressedHybridPlan.isRelevant(
                LocalDate.of(2026, 9, 5), safeSessions, includeClearedUpperBody = true,
            ),
        )

        val refreshed = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = true,
            today = LocalDate.of(2026, 9, 5),
            generatedAtEpochMillis = 2,
        )
        assertTrue(refreshed.sessions.all { it.plannedEpochDay >= LocalDate.of(2026, 9, 5).toEpochDay() })
        assertTrue(refreshed.sessions.any { it.id.endsWith("-upper") })
        assertFalse(refreshed.sessions.any { it.plannedEpochDay == LocalDate.of(2026, 9, 4).toEpochDay() })
    }

    @Test
    fun proposalRemainsRelevantWhenAllExpectedSessionsExistButStaleFridayRemains() {
        val today = LocalDate.of(2026, 9, 1)
        val corrected = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = false,
            today = today,
            generatedAtEpochMillis = 1,
        )
        val sessionsWithStaleFriday = corrected.sessions.associate { it.id to it.plannedEpochDay } +
            ("progress-strength-${LocalDate.of(2026, 9, 4).toEpochDay()}" to LocalDate.of(2026, 9, 4).toEpochDay())

        assertTrue(
            ProgressedHybridPlan.isRelevant(
                today = today,
                existingPlannedSessions = sessionsWithStaleFriday,
                includeClearedUpperBody = false,
            ),
        )
    }

    @Test
    fun clearanceRefreshDoesNotCreateASecondWorkoutOnAnAlreadyCompletedDay() {
        val completedDay = LocalDate.of(2026, 9, 11).toEpochDay()
        val refreshed = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = true,
            today = LocalDate.of(2026, 9, 11),
            generatedAtEpochMillis = 2,
            completedEpochDays = setOf(completedDay),
        )

        assertFalse(refreshed.sessions.any { it.plannedEpochDay == completedDay })
    }
}
