package ru.yakovenko.mountainform.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.health.HealthSummary
import java.time.Instant
import java.time.temporal.ChronoUnit

class GarminSummaryTest {
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
