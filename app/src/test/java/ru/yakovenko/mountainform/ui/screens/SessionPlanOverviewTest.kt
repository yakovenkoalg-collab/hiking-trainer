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
        assertEquals(listOf(null, null, null), run.map { it.title })
        assertEquals(listOf("Ходьба перед бегом", "Лёгкий бег", "Ходьба после бега"), run.map { it.steps.single().title })

        val strength = planBlocks(WorkoutPlanCompiler.compile(sessions.first { it.type == "STRENGTH" }.steps))
        assertEquals(listOf(null, null, null, null, "Верх тела · 2 круга", "Икры и core · 2 круга", null), strength.map { it.title })
        assertEquals(listOf("calf", "bridge", "core", "forearm-plank"), strength[5].steps.map { it.id })

        val strides = planBlocks(WorkoutPlanCompiler.compile(sessions.first { "ускорения" in it.title }.steps))
        assertEquals(listOf(null, null, "Ускорения", null, null), strides.map { it.title })
    }

    @Test
    fun rowDoseAddsTheTargetOnlyWhenTheDoseDoesNotNameIt() {
        val easy = sessions.first().steps.first { it.id == "easy-run" }
        assertEquals("30 минут · пульс до 148", planRowDose(easy))
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
        assertNull(sessionTargetLabel(listOf(
            ExerciseStep("easy", "Лёгкий", "10 минут", "", targetHeartRateMax = 148),
            ExerciseStep("tempo", "Темп", "10 минут", "", targetHeartRateMax = 175),
        )))
    }
}
