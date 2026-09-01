package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.ShoulderLoadPhase

class ShoulderSafetyTest {
    @Test
    fun detectsTagsAndRiskWordsWithoutBlockingSafeLegWork() {
        assertTrue(ShoulderSafety.conflicts(step("safe-name", "Толчок гири", "Без боли")))
        assertTrue(ShoulderSafety.conflicts(step("tagged", "Упражнение", "", listOf("OVERHEAD"))))
        assertFalse(ShoulderSafety.conflicts(step("legs", "Подъём на ступень", "Колено по линии стопы")))
    }

    @Test
    fun safetyWarningInInstructionsDoesNotBecomeAFalseConflict() {
        assertFalse(
            ShoulderSafety.conflicts(
                step(
                    "core",
                    "Антиразгибание лёжа",
                    "Руки расслаблены. Гирю 16 кг до визита к врачу не используйте.",
                ),
            ),
        )
    }

    @Test
    fun therapistClearedExerciseNeedsExplicitLoadPhase() {
        val clearedExercise = step(
            "row",
            "Тяга с упором груди",
            "Нейтральный хват",
            listOf("SHOULDER_CLEARANCE_REQUIRED"),
        )

        assertTrue(ShoulderSafety.conflicts(clearedExercise, ShoulderLoadPhase.RESTRICTED))
        assertFalse(ShoulderSafety.conflicts(clearedExercise, ShoulderLoadPhase.THERAPIST_CLEARED))
        assertFalse(ShoulderSafety.conflicts(clearedExercise, ShoulderLoadPhase.RETURNING))
    }

    private fun step(id: String, title: String, instructions: String, tags: List<String> = emptyList()) =
        ExerciseStep(id, title, "1 × 8", instructions, restrictionTags = tags)
}
