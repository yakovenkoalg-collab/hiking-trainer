package ru.yakovenko.mountainform.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.targetLabel

class HeartRateTargetTest {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true }

    private fun lap(index: Int, seconds: Long, heartRate: Double?) =
        ActivityLapSummary(index = index, durationSeconds = seconds, averageHeartRate = heartRate)

    private fun session(id: String, epochDay: Long, steps: List<ExerciseStep>) = TrainingSessionEntity(
        id = id, plannedEpochDay = epochDay, title = id, type = "RUN", phase = "", objective = "",
        durationMinutes = 40, targetRpe = 3, stepsJson = json.encodeToString(steps),
    )

    @Test
    fun timeAboveCeilingIsEstimatedByLapsAndBoundedByWatchZones() {
        val compliance = requireNotNull(
            HeartRateTarget.compliance(
                targetMax = 148,
                averageHeartRate = 152.0,
                durationSeconds = 1_500,
                laps = listOf(lap(1, 400, 142.0), lap(2, 380, 149.0), lap(3, 370, 158.0), lap(4, 350, null)),
                // Garmin: one more bucket than boundaries; only buckets starting at 154 are certainly above 148.
                zoneHighBoundaries = listOf(96.0, 115.0, 134.0, 154.0, 173.0, 192.0),
                zoneSeconds = listOf(0.0, 10.0, 90.0, 900.0, 420.0, 80.0, 0.0),
            ),
        )
        assertEquals(HeartRateBasis.LAPS, compliance.basis)
        assertEquals(1_150, compliance.assessedSeconds)
        assertEquals(750, compliance.secondsAboveTarget)
        assertEquals(2, compliance.lapsAboveTarget)
        assertEquals(3, compliance.lapsAssessed)
        assertEquals(400, compliance.easySeconds)
        assertEquals(500L, compliance.definiteSecondsAbove)
        assertEquals(HeartRateVerdict.ABOVE, compliance.verdict)
    }

    @Test
    fun paceAtEasyHeartRateUsesOnlyFullLapsAtOrBelowTheCeiling() {
        val compliance = requireNotNull(
            HeartRateTarget.compliance(
                targetMax = 148, averageHeartRate = 147.0, durationSeconds = 1_700,
                laps = listOf(
                    ActivityLapSummary(1, 420, distanceMeters = 1_000.0, averageHeartRate = 140.0),
                    ActivityLapSummary(2, 400, distanceMeters = 1_000.0, averageHeartRate = 148.0),
                    ActivityLapSummary(3, 380, distanceMeters = 1_000.0, averageHeartRate = 153.0),
                    ActivityLapSummary(4, 90, distanceMeters = 220.0, averageHeartRate = 146.0),
                    ActivityLapSummary(5, 410, averageHeartRate = 145.0),
                ),
            ),
        )
        assertEquals(820, compliance.easyPaceSeconds)
        assertEquals(2_000.0, compliance.easyPaceMeters, 0.01)
        assertEquals(1_320, compliance.easySeconds)
    }

    @Test
    fun targetLabelDescribesHeartRateAndPace() {
        val step = ExerciseStep("tempo", "Темповый отрезок", "3 × 8 минут", "")
        assertNull(step.targetLabel())
        assertEquals("пульс до 148", step.copy(targetHeartRateMax = 148).targetLabel())
        assertEquals("пульс 168–175", step.copy(targetHeartRateMin = 168, targetHeartRateMax = 175).targetLabel())
        assertEquals(
            "темп 5:15–5:20/км · пульс 168–175",
            step.copy(targetHeartRateMin = 168, targetHeartRateMax = 175, targetPaceFastSecPerKm = 315,
                targetPaceSlowSecPerKm = 320).targetLabel(),
        )
        assertEquals("темп 4:58/км", step.copy(targetPaceFastSecPerKm = 298).targetLabel())
    }

    @Test
    fun verdictSeparatesWithinPartlyAndAbove() {
        fun verdict(vararg heartRates: Double) = requireNotNull(
            HeartRateTarget.compliance(148, null, 0, heartRates.mapIndexed { index, rate -> lap(index + 1, 300, rate) }),
        ).verdict
        assertEquals(HeartRateVerdict.WITHIN, verdict(140.0, 144.0, 148.0))
        assertEquals(HeartRateVerdict.PARTLY_ABOVE, verdict(140.0, 144.0, 151.0))
        assertEquals(HeartRateVerdict.ABOVE, verdict(140.0, 151.0, 155.0))
    }

    @Test
    fun withoutLapsTheAverageDecidesAndMissingHeartRateGivesNoVerdict() {
        val above = requireNotNull(HeartRateTarget.compliance(148, 156.0, 1_800, emptyList()))
        assertEquals(HeartRateBasis.AVERAGE, above.basis)
        assertEquals(1_800, above.secondsAboveTarget)
        assertNull(above.definiteSecondsAbove)
        assertEquals(0, requireNotNull(HeartRateTarget.compliance(148, 148.0, 1_800, emptyList())).secondsAboveTarget)
        assertNull(HeartRateTarget.compliance(148, null, 1_800, emptyList()))
    }

    @Test
    fun sessionCeilingComesFromStepsAndCurrentCeilingFollowsTheCalendar() {
        val easy = ExerciseStep("easy-run", "Лёгкий бег", "30 минут", "", targetHeartRateMax = 148)
        val steady = ExerciseStep("steady", "Ровный бег", "20 минут", "", targetHeartRateMax = 160)
        val walk = ExerciseStep("warmup", "Ходьба", "5 минут", "")
        assertEquals(160, HeartRateTarget.sessionTarget(listOf(walk, easy, steady)))
        assertNull(HeartRateTarget.sessionTarget(listOf(walk)))
        val sessions = listOf(
            session("past", 100, listOf(easy)),
            session("strength", 104, listOf(walk)),
            session("future", 110, listOf(steady)),
        )
        assertEquals(148, HeartRateTarget.currentTarget(sessions, todayEpochDay = 105))
        assertEquals(160, HeartRateTarget.currentTarget(sessions, todayEpochDay = 110))
        assertEquals(148, HeartRateTarget.currentTarget(sessions, todayEpochDay = 50))
        assertNull(HeartRateTarget.currentTarget(listOf(session("strength", 104, listOf(walk))), 105))
    }

    @Test
    fun storedStepsWithoutCeilingStillDecodeAndAbsentCeilingIsNotWritten() {
        val stored = """[{"id":"easy-run","title":"Лёгкий бег","prescription":"30 минут","instructions":""}]"""
        val decoded = json.decodeFromString<List<ExerciseStep>>(stored)
        assertNull(decoded.single().targetHeartRateMax)
        assertFalse("targetHeartRateMax" in json.encodeToString(decoded))
        val withCeiling = json.encodeToString(listOf(decoded.single().copy(targetHeartRateMax = 148)))
        assertEquals(148, json.decodeFromString<List<ExerciseStep>>(withCeiling).single().targetHeartRateMax)
    }
}
