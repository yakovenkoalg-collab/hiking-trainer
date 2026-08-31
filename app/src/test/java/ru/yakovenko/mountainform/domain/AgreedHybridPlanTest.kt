package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.data.seedExerciseCatalog
import java.time.LocalDate

class AgreedHybridPlanTest {
    @Test
    fun agreedBlockUsesExactDatesAndShoulderSafeMovements() {
        val plan = AgreedHybridPlan.envelope(generatedAtEpochMillis = 1)

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 30),
            ).map(LocalDate::toEpochDay),
            plan.sessions.map { it.plannedEpochDay },
        )
        assertEquals(listOf(30, 50, 65, 50), plan.sessions.map { it.durationMinutes })
        assertEquals(LocalDate.of(2026, 8, 21).toEpochDay(), plan.replacePlannedFromEpochDay)
        assertEquals(LocalDate.of(2026, 8, 30).toEpochDay(), plan.replacePlannedThroughEpochDay)

        val text = plan.sessions.flatMap { it.steps }
            .joinToString(" ") { "${it.id} ${it.title} ${it.instructions} ${it.restrictionTags}" }
            .lowercase()
        listOf("гир", "вис", "брусь", "планк", "румын", "над головой").forEach { risk ->
            assertFalse("План содержит рискованное движение: $risk", text.contains(risk))
        }
        assertTrue(text.contains("без рюкзака"))
        assertTrue(plan.sessions[0].steps.any { it.prescription.contains("140–150") })
        assertTrue(plan.sessions[3].steps.any { it.prescription.contains("155") && !it.required })
        val catalogIds = seedExerciseCatalog(Json.Default).mapTo(mutableSetOf()) { it.id }
        assertTrue(plan.sessions.flatMap { it.steps }.all { it.exerciseId in catalogIds })
    }

    @Test
    fun blockStopsBeingCurrentAfterItWasAppliedOrItsWindowPassed() {
        assertTrue(AgreedHybridPlan.isRelevant(LocalDate.of(2026, 8, 20), emptySet()))
        assertFalse(AgreedHybridPlan.isRelevant(LocalDate.of(2026, 8, 20), AgreedHybridPlan.sessionIds))
        assertFalse(AgreedHybridPlan.isRelevant(LocalDate.of(2026, 8, 30), emptySet()))
        assertFalse(AgreedHybridPlan.isRelevant(LocalDate.of(2026, 8, 31), emptySet()))
    }
}
