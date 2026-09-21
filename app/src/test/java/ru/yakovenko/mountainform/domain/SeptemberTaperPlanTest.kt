package ru.yakovenko.mountainform.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.yakovenko.mountainform.data.PlanEnvelope
import java.io.File
import java.time.LocalDate

class SeptemberTaperPlanTest {
    @Test fun agreedDaysAndRunningDurationAreExact() {
        val sessions = SeptemberTaperPlan.sessions()
        assertEquals(listOf(22, 25, 27), sessions.map { LocalDate.ofEpochDay(it.plannedEpochDay).dayOfMonth })
        assertTrue(sessions.all { it.type == "RUN" })
        assertEquals(listOf(1500, 2100, 2700), sessions.map { session ->
            WorkoutPlanCompiler.compile(session.steps).filter { it.step.id !in setOf("warmup", "cooldown") }
                .sumOf { it.workSeconds ?: 0 }
        })
        val intervals = WorkoutPlanCompiler.compile(sessions[1].steps).filter { it.blockId == "strides" }
        assertEquals(listOf(15, 90, 15, 90, 15, 90, 15, 90), intervals.map { it.workSeconds })
        assertTrue(intervals.all { !it.step.required && it.restAfterSeconds == 0 })
    }

    @Test fun exportAndLaterPreviewKeepTheSameRevisionAndExcludePastDates() {
        val plan = SeptemberTaperPlan.envelope(LocalDate.of(2026, 9, 21), 1789977600000L)
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val encoded = json.encodeToString(plan)
        assertEquals(plan, json.decodeFromString<PlanEnvelope>(encoded))
        val later = SeptemberTaperPlan.envelope(LocalDate.of(2026, 9, 25))
        assertEquals(plan.planId, later.planId)
        assertEquals(2, later.sessions.size)
        assertTrue(later.sessions.all { it.plannedEpochDay >= later.replacePlannedFromEpochDay!! })
        val output = File("build/outputs/plans/september-taper-2026-09-22.mountain-plan.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(encoded)
    }
}
