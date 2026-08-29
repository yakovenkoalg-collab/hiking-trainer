package ru.yakovenko.mountainform.ui.screens

import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.health.HealthSummary
import java.time.temporal.ChronoUnit

internal data class GarminActivitySummary(
    val text: String,
    val needsAttention: Boolean,
)

internal data class LinkedGarminGroup(
    val typeLabel: String,
    val count: Int,
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val elevationMeters: Double?,
    val averageHeartRate: Double?,
    val maxHeartRate: Double?,
)

internal fun linkedGarminGroups(activities: List<ImportedActivityEntity>): List<LinkedGarminGroup> =
    activities
        .groupBy { friendlyActivityType(it.activityType) }
        .map { (typeLabel, group) ->
            val totalDuration = group.sumOf { it.durationSeconds.coerceAtLeast(0) }
            val heartRateDuration = group.sumOf { activity ->
                activity.durationSeconds.takeIf { activity.averageHeartRate != null && it > 0 } ?: 0
            }
            LinkedGarminGroup(
                typeLabel = typeLabel,
                count = group.size,
                durationSeconds = totalDuration,
                distanceMeters = group.mapNotNull { it.distanceMeters }.takeIf { it.isNotEmpty() }?.sum(),
                elevationMeters = group.mapNotNull { it.elevationMeters }.takeIf { it.isNotEmpty() }?.sum(),
                averageHeartRate = heartRateDuration.takeIf { it > 0 }?.let { duration ->
                    group.sumOf { activity ->
                        (activity.averageHeartRate ?: 0.0) *
                            activity.durationSeconds.takeIf { activity.averageHeartRate != null && it > 0 }.orZero()
                    } / duration
                },
                maxHeartRate = group.mapNotNull { it.maxHeartRate }.maxOrNull(),
            )
        }
        .sortedBy { it.typeLabel }

internal fun garminActivitySummary(
    activities: List<ImportedActivityEntity>,
    healthSummary: HealthSummary,
    nowEpochMillis: Long = System.currentTimeMillis(),
): GarminActivitySummary {
    val visibleActivities = activitiesWithinWindow(
        activities = activities,
        windowDays = healthSummary.windowDays,
        nowEpochMillis = nowEpochMillis,
    )
    val unlinked = visibleActivities.count { it.status == ActivityLinkStatus.UNLINKED }
    return when {
        unlinked > 0 -> GarminActivitySummary(
            text = "${activityCount(unlinked)} за ${healthSummary.windowDays} дн. нужно проверить",
            needsAttention = true,
        )
        visibleActivities.isNotEmpty() -> GarminActivitySummary(
            text = "${activityCount(visibleActivities.size)} за ${healthSummary.windowDays} дн. · всё разобрано",
            needsAttention = false,
        )
        healthSummary.hasAnyData -> GarminActivitySummary("Данные получены", false)
        healthSummary.permissionsGranted -> GarminActivitySummary("Доступ есть, записей пока нет", false)
        else -> GarminActivitySummary("Требуется подключение", false)
    }
}

internal fun activitiesWithinWindow(
    activities: List<ImportedActivityEntity>,
    windowDays: Int,
    nowEpochMillis: Long = System.currentTimeMillis(),
): List<ImportedActivityEntity> {
    val days = windowDays.coerceIn(7, 90)
    val startEpochMillis = java.time.Instant.ofEpochMilli(nowEpochMillis)
        .minus(days.toLong(), ChronoUnit.DAYS)
        .toEpochMilli()
    return activities
        .asSequence()
        .filter { it.startAtEpochMillis in startEpochMillis..nowEpochMillis }
        .sortedByDescending { it.startAtEpochMillis }
        .toList()
}

private fun activityCount(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "активностей"
        count % 10 == 1 -> "активность"
        count % 10 in 2..4 -> "активности"
        else -> "активностей"
    }
    return "$count $word"
}

private fun Long?.orZero(): Long = this ?: 0L
