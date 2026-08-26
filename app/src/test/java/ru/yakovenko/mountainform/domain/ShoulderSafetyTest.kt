package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ExerciseStep

class ShoulderSafetyTest {
    @Test
    fun detectsTagsAndRiskWordsWithoutBlockingSafeLegWork() {
        assertTrue(ShoulderSafety.conflicts(step("safe-name", "Толчок гири", "Без боли")))
        assertTrue(ShoulderSafety.conflicts(step("tagged", "Упражнение", "", listOf("OVERHEAD"))))
        assertFalse(ShoulderSafety.conflicts(step("legs", "Подъём на ступень", "Колено по линии стопы")))
    }

    private fun step(id: String, title: String, instructions: String, tags: List<String> = emptyList()) =
        ExerciseStep(id, title, "1 × 8", instructions, restrictionTags = tags)
}
