package ru.yakovenko.mountainform.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.health.HealthSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class GarminSummaryTest {
    @Test
    fun linkedActivitiesAreAggregatedByType() {
        val first = activity("first", ActivityLinkStatus.LINKED, 1_000).copy(
            activityType = "BIKING",
            durationSeconds = 3_600,
            distanceMeters = 20_000.0,
            elevationMeters = 120.0,
            averageHeartRate = 150.0,
            maxHeartRate = 180.0,
        )
        val second = activity("second", ActivityLinkStatus.LINKED, 2_000).copy(
            activityType = "CYCLING",
            durationSeconds = 1_800,
            distanceMeters = 10_000.0,
            elevationMeters = 80.0,
            averageHeartRate = 160.0,
            maxHeartRate = 190.0,
        )
        val run = activity("run", ActivityLinkStatus.LINKED, 3_000).copy(
            activityType = "RUNNING",
            durationSeconds = 600,
            distanceMeters = 1_500.0,
        )

        val groups = linkedGarminGroups(listOf(first, second, run))

        val bike = groups.single { it.typeLabel == "Велосипед" }
        assertEquals(2, bike.count)
        assertEquals(5_400, bike.durationSeconds)
        assertEquals(30_000.0, bike.distanceMeters!!, 0.01)
        assertEquals(200.0, bike.elevationMeters!!, 0.01)
        assertEquals(153.33, bike.averageHeartRate!!, 0.01)
        assertEquals(190.0, bike.maxHeartRate!!, 0.01)
        assertNull(groups.single { it.typeLabel == "Бег" }.averageHeartRate)
    }

    @Test
    fun importedFitIsShownEvenWithoutHealthConnectData() {
        val summary = garminActivitySummary(
            activities = listOf(activity("linked", ActivityLinkStatus.LINKED)),
            healthSummary = HealthSummary(),
        )

        assertEquals("1 активность за 30 дн. · всё разобрано", summary.text)
        assertFalse(summary.needsAttention)
    }

    @Test
    fun unlinkedImportedActivitiesTakePriority() {
        val summary = garminActivitySummary(
            activities = listOf(
                activity("one", ActivityLinkStatus.UNLINKED),
                activity("two", ActivityLinkStatus.UNLINKED),
                activity("ignored", ActivityLinkStatus.IGNORED),
            ),
            healthSummary = HealthSummary(available = true, permissionsGranted = true, workouts = 3),
        )

        assertEquals("2 активности за 30 дн. нужно проверить", summary.text)
        assertTrue(summary.needsAttention)
    }

    @Test
    fun movementUnitsDependOnSport() {
        assertEquals("20.0 км/ч", formatMovementMetric("MOUNTAIN_BIKING", 3_600, 20_000.0))
        assertEquals("5:00/км", formatMovementMetric("TRAIL_RUNNING", 1_500, 5_000.0))
        assertEquals("об/мин", cadenceUnit("INDOOR_CYCLING"))
        assertEquals("шаг/мин", cadenceUnit("WALKING"))
    }

    @Test
    fun savedFitHistoryUsesSelectedWindowWithoutDeletingOlderRecords() {
        val now = Instant.parse("2026-08-26T12:00:00Z")
        val recent = activity("recent", ActivityLinkStatus.UNLINKED, now.minus(3, ChronoUnit.DAYS).toEpochMilli())
        val older = activity("older", ActivityLinkStatus.UNLINKED, now.minus(20, ChronoUnit.DAYS).toEpochMilli())
        val future = activity("future", ActivityLinkStatus.UNLINKED, now.plus(1, ChronoUnit.DAYS).toEpochMilli())
        val all = listOf(older, future, recent)

        assertEquals(listOf("recent"), activitiesWithinWindow(all, 7, now.toEpochMilli()).map { it.id })
        assertEquals(listOf("recent", "older"), activitiesWithinWindow(all, 30, now.toEpochMilli()).map { it.id })
        assertEquals(3, all.size)
    }

    @Test
    fun sessionPickerUsesSevenDayWindowAndExcludesUnavailableLinks() {
        val sessionDay = LocalDate.of(2026, 8, 28)
        val session = TrainingSessionEntity(
            id = "app-session",
            plannedEpochDay = sessionDay.toEpochDay(),
            title = "Велосипед + комплекс",
            type = "HYBRID",
            phase = "BASE",
            objective = "test",
            durationMinutes = 60,
            targetRpe = 5,
            stepsJson = "[]",
        )
        fun at(dayOffset: Long) = sessionDay.plusDays(dayOffset)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val candidates = listOf(
            activity("minus-seven", ActivityLinkStatus.UNLINKED, at(-7)),
            activity("plus-seven", ActivityLinkStatus.UNLINKED, at(7)),
            activity("outside", ActivityLinkStatus.UNLINKED, at(8)),
            activity("ignored", ActivityLinkStatus.IGNORED, at(0)),
            activity("linked-here", ActivityLinkStatus.LINKED, at(1)).copy(linkedSessionId = session.id),
            activity("linked-elsewhere", ActivityLinkStatus.LINKED, at(2)).copy(linkedSessionId = "other"),
        )

        assertEquals(
            listOf("plus-seven", "linked-here", "minus-seven"),
            sessionGarminCandidates(session, candidates).map { it.id },
        )
    }

    private fun activity(
        id: String,
        status: String,
        startAtEpochMillis: Long = System.currentTimeMillis() - 1_000,
    ) = ImportedActivityEntity(
        id = id,
        sourceRecordId = "record-$id",
        sourceType = ActivitySourceType.FIT,
        sourcePackage = "fit",
        title = id,
        activityType = "RUNNING",
        startAtEpochMillis = startAtEpochMillis,
        endAtEpochMillis = startAtEpochMillis + 1_000,
        durationSeconds = 1,
        status = status,
        importedAtEpochMillis = 3,
    )
}
