package ru.yakovenko.mountainform.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import java.time.LocalDate
import java.time.ZoneOffset

class RunningVolumeTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 14) // Wednesday

    private fun run(
        id: String,
        date: LocalDate,
        kilometres: Double,
        lapHeartRates: List<Double> = emptyList(),
        type: String = "RUNNING",
        source: String = ActivitySourceType.FIT,
        status: String = ActivityLinkStatus.UNLINKED,
        linkedSessionId: String? = null,
        startHour: Int = 18,
    ): ImportedActivityEntity {
        val start = date.atTime(startHour, 0).toInstant(zone).toEpochMilli()
        val seconds = (kilometres * 400).toLong()
        val laps = lapHeartRates.mapIndexed { index, rate ->
            ActivityLapSummary(index + 1, seconds / lapHeartRates.size, averageHeartRate = rate)
        }
        return ImportedActivityEntity(
            id = id, sourceRecordId = id, sourceType = source, sourcePackage = "test", title = id,
            activityType = type, startAtEpochMillis = start, endAtEpochMillis = start + seconds * 1000,
            durationSeconds = seconds, distanceMeters = kilometres * 1000, lapsJson = Json.encodeToString(laps),
            linkedSessionId = linkedSessionId, status = status, importedAtEpochMillis = start,
        )
    }

    private fun session(id: String, date: LocalDate, ceiling: Int?) = TrainingSessionEntity(
        id = id, plannedEpochDay = date.toEpochDay(), title = id, type = "RUN", phase = "", objective = "",
        durationMinutes = 40, targetRpe = 3,
        stepsJson = Json.encodeToString(listOf(ExerciseStep("easy-run", "Бег", "30 минут", "", targetHeartRateMax = ceiling))),
    )

    @Test
    fun weeksRunMondayToSundayAndOnlyRunsCount() {
        val weeks = RunningVolume.weeks(
            activities = listOf(
                run("sunday-before", LocalDate.of(2026, 10, 11), 8.0),
                run("monday", LocalDate.of(2026, 10, 12), 5.0),
                run("tuesday", LocalDate.of(2026, 10, 13), 6.0, type = "Бег"),
                run("ride", LocalDate.of(2026, 10, 13), 20.0, type = "CYCLING"),
                run("hike", LocalDate.of(2026, 10, 13), 12.0, type = "HIKING"),
                run("ignored", LocalDate.of(2026, 10, 13), 7.0, status = ActivityLinkStatus.IGNORED, startHour = 7),
                run("old", LocalDate.of(2026, 7, 1), 9.0),
            ),
            sessions = emptyList(), today = today, count = 8, zone = zone,
        )
        assertEquals(8, weeks.size)
        assertEquals(LocalDate.of(2026, 8, 24).toEpochDay(), weeks.first().weekStartEpochDay)
        assertEquals(LocalDate.of(2026, 10, 12).toEpochDay(), weeks.last().weekStartEpochDay)
        assertEquals(2, weeks.last().runs)
        assertEquals(11_000.0, weeks.last().distanceMeters, 0.01)
        assertEquals(8_000.0, weeks[6].distanceMeters, 0.01)
        assertTrue(weeks.take(6).all { it.runs == 0 })
        assertNull(weeks.last().easyShare)
    }

    @Test
    fun easyShareUsesTheLinkedSessionCeilingOrTheCurrentOne() {
        val sessions = listOf(
            session("easy", LocalDate.of(2026, 10, 13), 148),
            session("steady", LocalDate.of(2026, 10, 12), 160),
        )
        val weeks = RunningVolume.weeks(
            activities = listOf(
                // Linked to the steady session: all four laps are at or below 160.
                run("steady-run", LocalDate.of(2026, 10, 12), 4.0, listOf(150.0, 155.0, 158.0, 160.0), linkedSessionId = "steady"),
                // Unlinked: judged by the ceiling in force today (148) — two of four laps are above.
                run("free-run", LocalDate.of(2026, 10, 13), 4.0, listOf(140.0, 146.0, 150.0, 156.0)),
                run("no-heart-rate", LocalDate.of(2026, 10, 14), 4.0, startHour = 7),
            ),
            sessions = sessions, today = today, count = 2, zone = zone,
        )
        val week = weeks.last()
        assertEquals(3, week.runs)
        assertEquals(3_200, week.assessedSeconds)
        assertEquals(2_400, week.easySeconds)
        assertEquals(0.75, requireNotNull(week.easyShare), 0.001)
    }

    @Test
    fun theSameRunFromHealthConnectAndFitIsCountedOncePreferringFit() {
        val date = LocalDate.of(2026, 10, 13)
        val weeks = RunningVolume.weeks(
            activities = listOf(
                run("hc", date, 8.0, source = ActivitySourceType.HEALTH_CONNECT),
                run("fit", date, 8.1, listOf(140.0, 141.0)),
                run("evening", date, 3.0, startHour = 21),
            ),
            sessions = listOf(session("easy", date, 148)), today = today, count = 1, zone = zone,
        )
        assertEquals(2, weeks.single().runs)
        assertEquals(11_100.0, weeks.single().distanceMeters, 0.01)
        assertEquals(1.0, requireNotNull(weeks.single().easyShare), 0.001)
    }
}
