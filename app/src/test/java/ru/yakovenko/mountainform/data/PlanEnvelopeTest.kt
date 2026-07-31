package ru.yakovenko.mountainform.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEnvelopeTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun planRoundTripKeepsRestrictionTags() {
        val plan = PlanEnvelope(
            planId = "test-plan",
            author = "coach",
            reason = "test",
            generatedAtEpochMillis = 1,
            sessions = listOf(
                PlanSession(
                    id = "s1",
                    plannedEpochDay = 2,
                    title = "Test",
                    type = "STRENGTH",
                    phase = "BASE",
                    objective = "Verify",
                    durationMinutes = 60,
                    targetRpe = 5,
                    steps = listOf(
                        ExerciseStep(
                            id = "overhead",
                            title = "Overhead",
                            prescription = "3 × 5",
                            instructions = "test",
                            restrictionTags = listOf("OVERHEAD"),
                        ),
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<PlanEnvelope>(json.encodeToString(plan))

        assertEquals("test-plan", decoded.planId)
        assertTrue(decoded.sessions.single().steps.single().restrictionTags.contains("OVERHEAD"))
    }
}
