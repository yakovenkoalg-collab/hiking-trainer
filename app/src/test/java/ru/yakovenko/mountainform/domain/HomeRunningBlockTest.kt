package ru.yakovenko.mountainform.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import ru.yakovenko.mountainform.data.ShoulderLoadPhase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.data.PlanEnvelope
import java.io.File

class HomeRunningBlockTest {
    @Test fun exportProposalRoundTripsWithoutChangingRevision() {
        val plan = HomeRunningBlock.envelope(LocalDate.of(2026, 9, 10), generatedAtEpochMillis = 1788998400000L)
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val encoded = json.encodeToString(plan)
        assertEquals(plan, json.decodeFromString<PlanEnvelope>(encoded))
        val output = File("build/outputs/plans/home-running-2026-09-10.mountain-plan.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(encoded)
    }

    @Test fun nextWeekHasFourDaysAndThreeRuns() {
        val week = HomeRunningBlock.sessions().filter { it.plannedEpochDay >= LocalDate.of(2026, 9, 14).toEpochDay() }
        assertEquals(listOf(15, 17, 18, 20), week.map { LocalDate.ofEpochDay(it.plannedEpochDay).dayOfMonth })
        assertEquals(3, week.count { it.type == "RUN" || it.type == "HYBRID" })
        assertTrue(week.none { it.title.contains("зал") })
    }

    @Test fun checkingOnDifferentDaysKeepsTheSameRevision() {
        assertEquals(HomeRunningBlock.envelope(LocalDate.of(2026, 9, 10)).planId,
            HomeRunningBlock.envelope(LocalDate.of(2026, 9, 16)).planId)
        assertTrue(HomeRunningBlock.envelope(LocalDate.of(2026, 9, 16)).sessions.all {
            it.plannedEpochDay >= LocalDate.of(2026, 9, 16).toEpochDay()
        })
    }

    @Test fun stridesAlternateWithRecoveryAndTotalFortyMinutes() {
        val session = HomeRunningBlock.sessions().single { LocalDate.ofEpochDay(it.plannedEpochDay).dayOfMonth == 15 }
        val targets = WorkoutPlanCompiler.compile(session.steps)
        val intervals = targets.filter { it.blockId == "strides" }
        assertEquals(8, intervals.size)
        assertEquals(listOf(15, 90, 15, 90, 15, 90, 15, 90), intervals.map { it.workSeconds })
        assertEquals(2400, targets.filter { it.step.id in setOf("easy-run", "stride", "stride-recovery", "easy-run-finish") }.sumOf { it.workSeconds ?: 0 })
        assertTrue(intervals.all { it.restAfterSeconds == 0 })
    }

    @Test fun shoulderWorkNeedsClearanceAndDoesNotIncludeSymptomaticPushWork() {
        val steps = HomeRunningBlock.sessions().flatMap { it.steps }
        val upper = steps.filter { it.id == "pull-up" || it.id == "forearm-plank" }
        assertTrue(upper.isNotEmpty())
        assertTrue(upper.all { ShoulderSafety.conflicts(it, ShoulderLoadPhase.RESTRICTED) })
        assertTrue(upper.none { ShoulderSafety.conflicts(it, ShoulderLoadPhase.THERAPIST_CLEARED) })
        assertTrue(steps.none { it.title.contains("отжим", true) || it.title.contains("берпи", true) })
    }
}
