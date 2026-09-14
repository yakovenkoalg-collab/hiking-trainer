package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.WorkoutBlockType
import ru.yakovenko.mountainform.data.imageKey

class WorkoutPlanCompilerTest {
    @Test
    fun timedDosageNeverBecomesRepetitions() {
        for (prescription in listOf("2 × 40 сек · вместо утренней", "2 × 10 минут")) {
            val targets = WorkoutPlanCompiler.compile(listOf(ExerciseStep("plank", "Планка", prescription, "")))
            assertEquals(2, targets.size)
            assertEquals(null, targets.first().plannedReps)
        }
        val target = WorkoutPlanCompiler.compile(listOf(ExerciseStep("plank", "Планка", "2 × 40 сек", ""))).first()
        assertEquals(40, target.workSeconds)
        assertEquals("40 сек", target.doseLabel())
    }

    @Test
    fun retrospectiveEntryDoesNotInventRepsTimeOrRest() {
        val target = WorkoutPlanCompiler.compile(listOf(ExerciseStep("squat", "Присед", "3 × 8 на ногу", "", restSeconds = 60))).first()
        val log = retrospectiveSetLog("session", target, 12345)
        assertEquals(null, log.startedAtEpochMillis)
        assertEquals(null, log.actualReps)
        assertEquals(null, log.actualRestSeconds)
        assertEquals(0, log.elapsedSeconds)
        assertEquals("NOT_USED", log.timingStatus)
        assertEquals("8 повторений на каждую сторону", target.doseLabel())
    }

    @Test
    fun legacySingleLegArtworkResolvesWithoutPlanMutation() {
        val original = ExerciseStep("calf", "Подъём на носок одной ногой", "2 × 12 на ногу", "", illustrationKey = "calf-raise")
        assertEquals("single-leg-calf-raise", original.imageKey())
        assertEquals("calf-raise", original.illustrationKey)
        assertEquals("single-leg-bridge", ExerciseStep("bridge", "Ягодичный мост на одной ноге", "", "").imageKey())
        assertEquals("bridge", ExerciseStep("bridge", "Ягодичный мост", "", "").imageKey())
    }
    @Test
    fun restIsPreservedBetweenStrengthExercisesButNotAfterWorkout() {
        val targets = WorkoutPlanCompiler.compile(listOf(
            ExerciseStep("first", "Первое", "2 × 8", "", sets = 2, reps = 8, restSeconds = 90),
            ExerciseStep("second", "Второе", "2 × 10", "", sets = 2, reps = 10, restSeconds = 60),
        ))
        assertEquals(listOf(90, 90, 60, 0), targets.map { it.restAfterSeconds })
    }

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
