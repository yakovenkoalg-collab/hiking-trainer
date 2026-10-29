package ru.yakovenko.mountainform.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.ShoulderLoadPhase
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class BaseOneBlockTest {
    private val sessions = BaseOneBlock.sessions()

    private fun runSeconds(steps: List<ru.yakovenko.mountainform.data.ExerciseStep>) =
        WorkoutPlanCompiler.compile(steps).filter { it.step.id !in setOf("warmup", "cooldown") }
            .sumOf { it.workSeconds ?: 0 }

    @Test
    fun fourSessionsEveryWeekOnAgreedDays() {
        assertEquals(20, sessions.size)
        assertEquals(sessions.map { it.id }.distinct().size, sessions.size)
        val byWeek = sessions.groupBy {
            LocalDate.ofEpochDay(it.plannedEpochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        assertEquals(5, byWeek.size)
        byWeek.values.forEach { week ->
            assertEquals(
                listOf(DayOfWeek.TUESDAY to "RUN", DayOfWeek.WEDNESDAY to "STRENGTH", DayOfWeek.FRIDAY to "RUN",
                    DayOfWeek.SUNDAY to "RUN"),
                week.map { LocalDate.ofEpochDay(it.plannedEpochDay).dayOfWeek to it.type },
            )
        }
    }

    @Test
    fun runningVolumeGrowsThenDeloadsAndCardsMatchTheirSteps() {
        val weeklyMinutes = sessions.filter { it.type == "RUN" }
            .groupBy { LocalDate.ofEpochDay(it.plannedEpochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .values.map { week -> week.sumOf { runSeconds(it.steps) } / 60 }
        assertEquals(listOf(110, 135, 150, 170, 130), weeklyMinutes)
        sessions.filter { it.type == "RUN" }.forEach { session ->
            val total = WorkoutPlanCompiler.compile(session.steps).sumOf { it.workSeconds ?: 0 }
            assertTrue("${session.title}: $total", kotlin.math.abs(total - session.durationMinutes * 60) <= 60)
        }
    }

    @Test
    fun everyEasyRunCarriesTheCeilingAndNothingElseDoes() {
        sessions.forEach { session ->
            session.steps.forEach { step ->
                val expected = when {
                    step.id == "calm-start" -> BaseOneBlock.CALM_START_HEART_RATE_MAX
                    step.exerciseId == "run-walk" -> BaseOneBlock.EASY_HEART_RATE_MAX
                    else -> null
                }
                assertEquals("${session.id}/${step.id}", expected, step.targetHeartRateMax)
            }
            val expectedSessionCeiling = if (session.type == "RUN") BaseOneBlock.EASY_HEART_RATE_MAX else null
            assertEquals(expectedSessionCeiling, HeartRateTarget.sessionTarget(session.steps))
        }
    }

    @Test
    fun dosesCompileWithoutInventedTimersAndStridesAlternateWithRecovery() {
        sessions.forEach { session ->
            WorkoutPlanCompiler.compile(session.steps).forEach { target ->
                assertTrue("${session.id}/${target.step.id}", (target.workSeconds == null) != (target.plannedReps == null))
                target.workSeconds?.let { assertTrue("${session.id}/${target.step.id}: $it", it >= 20) }
            }
        }
        val strides = WorkoutPlanCompiler.compile(sessions.first { "ускорения" in it.title }.steps)
            .filter { it.blockType == WorkoutBlockType.CIRCUIT }
        assertEquals(listOf(20, 90, 20, 90, 20, 90, 20, 90), strides.map { it.workSeconds })
    }

    @Test
    fun upperBodyStepsAreTaggedSoRestrictionsDecideAndNothingElseConflicts() {
        sessions.forEach { session ->
            assertTrue(session.steps.none { ShoulderSafety.conflicts(it, ShoulderLoadPhase.FULL) })
            val restricted = session.steps.filter { ShoulderSafety.conflicts(it, ShoulderLoadPhase.RESTRICTED) }.map { it.id }
            val tagged = session.steps.filter { it.restrictionTags.isNotEmpty() }.map { it.id }
            assertEquals(session.id, tagged, restricted)
            if (session.type == "STRENGTH") assertTrue(session.id, "pull-up" in tagged && "push-up" in tagged)
        }
    }

    @Test
    fun activeShoulderRestrictionLeavesOutClosedStepsAndKeepsEverythingElse() {
        val today = LocalDate.of(2026, 10, 12)
        val unrestricted = BaseOneBlock.envelope(today, 1)
        assertEquals(unrestricted, BaseOneBlock.envelope(today, 1, restrictedShoulderPhase = ShoulderLoadPhase.FULL))
        assertTrue("ограничением плеча" !in unrestricted.reason)

        val restricted = BaseOneBlock.envelope(today, 1, restrictedShoulderPhase = ShoulderLoadPhase.RESTRICTED)
        assertTrue(restricted.reason.startsWith("Упражнения, закрытые текущим ограничением плеча"))
        assertEquals(unrestricted.sessions.map { it.id }, restricted.sessions.map { it.id })
        restricted.sessions.zip(unrestricted.sessions).forEach { (kept, full) ->
            assertTrue(kept.steps.none { ShoulderSafety.conflicts(it, ShoulderLoadPhase.RESTRICTED) })
            if (full.type == "RUN") assertEquals(full, kept)
            else assertEquals(
                full.steps.filter { it.restrictionTags.isEmpty() }.map { it.id },
                kept.steps.map { it.id },
            )
        }
        val cleared = BaseOneBlock.sessions(ShoulderLoadPhase.THERAPIST_CLEARED).first { it.type == "STRENGTH" }
        assertEquals(listOf("warmup", "hinge", "split-squat", "step-down", "calf", "bridge", "core", "cooldown"),
            cleared.steps.map { it.id })
    }

    @Test
    fun exportKeepsRevisionExcludesPastDaysAndNeverTouchesToday() {
        val plan = BaseOneBlock.envelope(LocalDate.of(2026, 10, 12), 1791800000000L)
        val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }
        val encoded = json.encodeToString(plan)
        assertEquals(plan, json.decodeFromString<PlanEnvelope>(encoded))
        assertEquals(LocalDate.of(2026, 10, 13).toEpochDay(), plan.replacePlannedFromEpochDay)
        assertEquals(LocalDate.of(2026, 11, 15).toEpochDay(), plan.replacePlannedThroughEpochDay)
        val later = BaseOneBlock.envelope(LocalDate.of(2026, 10, 26))
        assertEquals(plan.planId, later.planId)
        assertEquals(12, later.sessions.size)
        assertTrue(later.sessions.all { it.plannedEpochDay >= requireNotNull(later.replacePlannedFromEpochDay) })
        assertEquals(BaseOneBlock.CALM_START_HEART_RATE_MAX,
            json.decodeFromString<PlanEnvelope>(encoded).sessions.first().steps.first().targetHeartRateMax)
        assertTrue(plan.sessions.filter { it.type == "RUN" }.flatMap { it.steps }.none { it.exerciseId == "walk" })
        val output = File("build/outputs/plans/base-one-2026-10-13.mountain-plan.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(encoded)
    }

    @Test
    fun fromNovemberStrengthIsHeavierAndMatchesTheGarminWorkout() {
        val progression = sessions.single { LocalDate.ofEpochDay(it.plannedEpochDay) == LocalDate.of(2026, 11, 4) }
        assertEquals("Дом · сила, прогрессия", progression.title)
        assertEquals(
            listOf("warmup", "kettlebell-swing", "single-leg-kb-deadlift", "goblet-split-squat", "bulgarian-split-squat",
                "step-down", "pull-up", "push-up", "calf", "bridge", "core", "forearm-plank", "cooldown"),
            progression.steps.map { it.id },
        )
        val targets = WorkoutPlanCompiler.compile(progression.steps)
        assertEquals(3, targets.count { it.step.id == "kettlebell-swing" })
        assertEquals(3, targets.count { it.step.id == "pull-up" })
        assertEquals(listOf(8, 8, 8), targets.filter { it.step.id == "goblet-split-squat" }.map { it.plannedReps })
        val deload = sessions.single { LocalDate.ofEpochDay(it.plannedEpochDay) == LocalDate.of(2026, 11, 11) }
        assertTrue(deload.steps.none { it.id == "bulgarian-split-squat" })
        assertEquals(2, WorkoutPlanCompiler.compile(deload.steps).count { it.step.id == "kettlebell-swing" })
        assertEquals("3 минуты", progression.steps.last().prescription)
        assertTrue(sessions.filter { it.plannedEpochDay >= LocalDate.of(2026, 10, 30).toEpochDay() }
            .flatMap { it.steps }.none { it.title == "Dead bug" })
    }
}
