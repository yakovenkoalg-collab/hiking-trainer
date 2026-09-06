package ru.yakovenko.mountainform.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.seedExerciseCatalog
import java.time.LocalDate

class HomeKettlebellWeekPlanTest {
    @Test
    fun agreedWeekUsesOnlyHomeEquipmentAndKeepsRunningAsARecoveryWeek() {
        val plan = HomeKettlebellWeekPlan.envelope(
            today = LocalDate.of(2026, 9, 6),
            generatedAtEpochMillis = 1,
        )

        assertTrue(plan.planId.startsWith("home-kettlebell-week-v1"))
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 13),
            ).map(LocalDate::toEpochDay),
            plan.sessions.map { it.plannedEpochDay },
        )
        assertEquals(listOf(60, 35, 80, 70), plan.sessions.map { it.durationMinutes })
        val strength = plan.sessions.single { it.type == "STRENGTH" }
        val allText = strength.steps.joinToString(" ") {
            "${it.id} ${it.title} ${it.prescription} ${it.instructions}"
        }
        assertTrue(allText.contains("16 кг"))
        assertFalse(allText.contains("жим ногами", ignoreCase = true))
        assertFalse(allText.contains("отжим", ignoreCase = true))
        assertFalse(allText.contains("брус", ignoreCase = true))
        assertFalse(allText.contains("над головой", ignoreCase = true))
        assertEquals(4, strength.steps.single { it.id == "kettlebell-deadlift" }.sets)
        assertEquals(3, strength.steps.single { it.id == "step-down" }.rounds)

        val catalogIds = seedExerciseCatalog(Json.Default).mapTo(mutableSetOf()) { it.id }
        assertTrue(plan.sessions.flatMap { it.steps }.all { it.exerciseId in catalogIds })
    }

    @Test
    fun proposalReplacesOldFutureWeekButNeverACompletedDay() {
        val today = LocalDate.of(2026, 9, 6)
        val completedDay = LocalDate.of(2026, 9, 8).toEpochDay()
        val oldPlan = mapOf(
            "progress-hybrid-${LocalDate.of(2026, 9, 8).toEpochDay()}" to completedDay,
            "progress-strength-${LocalDate.of(2026, 9, 11).toEpochDay()}-upper" to
                LocalDate.of(2026, 9, 11).toEpochDay(),
        )

        assertTrue(HomeKettlebellWeekPlan.isRelevant(today, oldPlan, setOf(completedDay)))
        val replacement = HomeKettlebellWeekPlan.envelope(
            today = today,
            generatedAtEpochMillis = 1,
            completedEpochDays = setOf(completedDay),
        )

        assertFalse(replacement.sessions.any { it.plannedEpochDay == completedDay })
        assertTrue(replacement.sessions.all { it.id.startsWith("home-week-") })
    }

    @Test
    fun proposalIsNotOfferedBeforeAgreementDate() {
        assertFalse(
            HomeKettlebellWeekPlan.isRelevant(
                today = LocalDate.of(2026, 9, 5),
                existingPlannedSessions = emptyMap(),
            ),
        )
    }
}
