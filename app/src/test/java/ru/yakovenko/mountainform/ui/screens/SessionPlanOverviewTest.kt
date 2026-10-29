package ru.yakovenko.mountainform.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.domain.BaseOneBlock
import ru.yakovenko.mountainform.domain.WorkoutPlanCompiler

class SessionPlanOverviewTest {
    private val sessions = BaseOneBlock.sessions()

    @Test
    fun objectiveBecomesShortSeparatePoints() {
        assertEquals(
            listOf("Пульс не выше 148, темп любой.", "Выше — шаг, пока не опустится до 140.", "На подъёмах — шаг.", "Возьмите воду."),
            objectivePoints("Пульс не выше 148, темп любой. Выше — шаг, пока не опустится до 140.  На подъёмах — шаг. Возьмите воду."),
        )
        assertEquals(listOf("Скорость 8,3–9,0 км/ч по пульсу"), objectivePoints(" Скорость 8,3–9,0 км/ч по пульсу "))
        assertTrue(objectivePoints("").isEmpty())
    }

    @Test
    fun everyBlockSessionReadsAsAFewShortPoints() {
        sessions.forEach { session ->
            val points = objectivePoints(session.objective)
            assertTrue("${session.title}: ${points.size}", points.size in 2..4)
            assertTrue(session.title, points.all { it.length <= 60 })
        }
    }

    @Test
    fun runIsThreeRowsWithoutHeadingsAndCircuitsKeepOneHeading() {
        val run = planBlocks(WorkoutPlanCompiler.compile(sessions.first { it.type == "RUN" }.steps))
        assertEquals(listOf(null, null), run.map { it.title })
        assertEquals(listOf("Спокойный старт", "Лёгкий бег"), run.map { it.steps.single().title })

        val strength = planBlocks(WorkoutPlanCompiler.compile(sessions.first { it.type == "STRENGTH" }.steps))
        assertEquals(listOf(null, null, null, null, "Верх тела · 2 круга", "Икры и core · 2 круга", null), strength.map { it.title })
        assertEquals(listOf("calf", "bridge", "core", "forearm-plank"), strength[5].steps.map { it.id })

        val strides = planBlocks(WorkoutPlanCompiler.compile(sessions.first { "ускорения" in it.title }.steps))
        assertEquals(listOf(null, null, "Ускорения", null), strides.map { it.title })
    }

    @Test
    fun rowDoseAddsTheTargetOnlyWhenTheDoseDoesNotNameIt() {
        val easy = sessions.first().steps.first { it.id == "easy-run" }
        assertEquals("20 минут · пульс до 148", planRowDose(easy))
        assertEquals("20 минут · пульс до 148", planRowDose(ExerciseStep("run", "Бег", "20 минут", "", targetHeartRateMax = 148)))
        assertEquals("3 × 8 минут · темп 5:15–5:20/км · пульс 168–175", planRowDose(
            ExerciseStep("tempo", "Темп", "3 × 8 минут", "", targetHeartRateMin = 168, targetHeartRateMax = 175,
                targetPaceFastSecPerKm = 315, targetPaceSlowSecPerKm = 320),
        ))
        assertEquals("", planRowDose(ExerciseStep("walk", "Ходьба", "", "")))
    }

    @Test
    fun sessionTargetIsShownOnlyWhenItIsTheSameForTheWholeSession() {
        assertEquals("пульс до 148", sessionTargetLabel(sessions.first().steps))
        assertNull(sessionTargetLabel(sessions.first { it.type == "STRENGTH" }.steps))
        assertEquals("пульс до 175", sessionTargetLabel(listOf(
            ExerciseStep("easy", "Лёгкий", "10 минут", "", workSeconds = 600, targetHeartRateMax = 148),
            ExerciseStep("tempo", "Темп", "20 минут", "", workSeconds = 1_200, targetHeartRateMax = 175),
        )))
    }

    @Test
    fun finishedWorkoutShowsWhatWasRecordedForEachExercise() {
        fun log(step: String, round: Int, set: Int, reps: Int?, kg: Double? = null, seconds: Int = 0) =
            ru.yakovenko.mountainform.data.SessionSetLogEntity(
                sessionId = "s", stepId = step, roundIndex = round, setIndex = set, plannedReps = reps, actualReps = reps,
                loadKg = kg, elapsedSeconds = seconds, completed = true,
            )
        val steps = listOf(
            ExerciseStep("swing", "Махи гирей", "3 × 15 · 16 кг", "", sets = 3, reps = 15),
            ExerciseStep("plank", "Планка", "2 × 60 сек", "", sets = 2, workSeconds = 60),
            ExerciseStep("push", "Отжимания", "2 × 20", "", sets = 2, reps = 20),
        )
        val targets = WorkoutPlanCompiler.compile(steps)
        val logs = listOf(log("swing", 1, 1, 15, 16.0), log("swing", 1, 2, 15, 16.0), log("swing", 1, 3, 12, 16.0),
            log("plank", 1, 1, null, seconds = 60), log("plank", 1, 2, null, seconds = 60))
        fun dose(id: String) = doneDose(targets.filter { it.step.id == id }, logs)
        assertEquals("3 из 3 · 15, 15, 12 · 16 кг", dose("swing"))
        assertEquals("2 из 2 · 1 мин", dose("plank"))
        assertEquals("не отмечено", dose("push"))
    }
}
