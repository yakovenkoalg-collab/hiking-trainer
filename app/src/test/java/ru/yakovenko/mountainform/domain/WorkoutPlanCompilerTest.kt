package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.WorkoutBlockType

class WorkoutPlanCompilerTest {
    @Test
    fun legacyPrescriptionBecomesIndividualSets() {
        val targets = WorkoutPlanCompiler.compile(
            listOf(ExerciseStep("squat", "Присед", "3 × 8", "", restSeconds = 75)),
        )

        assertEquals(3, targets.size)
        assertEquals(8, targets.first().plannedReps)
        assertEquals(75, targets.first().restAfterSeconds)
        assertEquals(0, targets.last().restAfterSeconds)
    }

    @Test
    fun circuitAlternatesExercisesByRound() {
        val targets = WorkoutPlanCompiler.compile(
            listOf(
                ExerciseStep("calf", "Икры", "3 × 12", "", blockId = "c", blockType = WorkoutBlockType.CIRCUIT, rounds = 3, sets = 1),
                ExerciseStep("core", "Core", "3 × 8", "", blockId = "c", blockType = WorkoutBlockType.CIRCUIT, rounds = 3, sets = 1, restAfterRoundSeconds = 60),
            ),
        )

        assertEquals(listOf("calf", "core", "calf", "core", "calf", "core"), targets.map { it.step.id })
        assertEquals(listOf(1, 1, 2, 2, 3, 3), targets.map { it.roundIndex })
        assertEquals(60, targets[1].restAfterSeconds)
    }
}
