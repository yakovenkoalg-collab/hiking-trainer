package ru.yakovenko.mountainform.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

data class HealthSummary(
    val available: Boolean = false,
    val permissionsGranted: Boolean = false,
    val workouts: Int = 0,
    val steps: Long = 0,
    val distanceKm: Double = 0.0,
    val elevationMeters: Double = 0.0,
    val latestSleepHours: Double? = null,
    val averageHeartRate: Double? = null,
    val dataOrigins: Set<String> = emptySet(),
    val windowDays: Int = 30,
    val refreshedAtEpochMillis: Long? = null,
    val error: String? = null,
) {
    val hasAnyData: Boolean
        get() = workouts > 0 || steps > 0 || distanceKm > 0.0 || elevationMeters > 0.0 ||
            latestSleepHours != null || averageHeartRate != null
}

class HealthConnectManager(private val context: Context) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    val permissionContract = PermissionController.createRequestPermissionResultContract()

    fun availability(): HealthAvailability = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        else -> HealthAvailability.UNAVAILABLE
    }

    suspend fun readSummary(windowDays: Int = 30): HealthSummary {
        if (availability() != HealthAvailability.AVAILABLE) {
            return HealthSummary(windowDays = windowDays, error = "Health Connect недоступен")
        }
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(permissions)) {
                return HealthSummary(
                    available = true,
                    permissionsGranted = false,
                    windowDays = windowDays,
                    refreshedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            val end = Instant.now()
            val start = end.minus(windowDays.coerceIn(7, 90).toLong(), ChronoUnit.DAYS)
            val range = TimeRangeFilter.between(start, end)
            val workoutRecords = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = range,
                ),
            ).records
            val sleepRecords = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            ).records
            val sleep = sleepRecords.firstOrNull()
            val heartRateRecords = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                    pageSize = 50,
                ),
            ).records
            val heartRates = heartRateRecords.flatMap { it.samples }.map { it.beatsPerMinute }
            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                    ),
                    timeRangeFilter = range,
                ),
            )
            HealthSummary(
                available = true,
                permissionsGranted = true,
                workouts = workoutRecords.size,
                steps = aggregate[StepsRecord.COUNT_TOTAL] ?: 0,
                distanceKm = aggregate[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0,
                elevationMeters = aggregate[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters ?: 0.0,
                latestSleepHours = sleep?.let {
                    Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
                },
                averageHeartRate = heartRates.takeIf { it.isNotEmpty() }?.average(),
                dataOrigins = buildSet {
                    workoutRecords.forEach { add(it.metadata.dataOrigin.packageName) }
                    sleepRecords.forEach { add(it.metadata.dataOrigin.packageName) }
                    heartRateRecords.forEach { add(it.metadata.dataOrigin.packageName) }
                },
                windowDays = windowDays,
                refreshedAtEpochMillis = System.currentTimeMillis(),
            )
        }.getOrElse { error ->
            HealthSummary(
                available = true,
                windowDays = windowDays,
                refreshedAtEpochMillis = System.currentTimeMillis(),
                error = error.message ?: "Не удалось прочитать Health Connect",
            )
        }
    }

    suspend fun readActivities(windowDays: Int = 30): List<ImportedActivityEntity> {
        if (availability() != HealthAvailability.AVAILABLE) return emptyList()
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(permissions)) return emptyList()
        val end = Instant.now()
        val start = end.minus(windowDays.coerceIn(7, 90).toLong(), ChronoUnit.DAYS)
        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 50,
            ),
        ).records
        val now = System.currentTimeMillis()
        return sessions.map { exercise ->
            val range = TimeRangeFilter.between(exercise.startTime, exercise.endTime)
            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        DistanceRecord.DISTANCE_TOTAL,
                        ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MAX,
                    ),
                    timeRangeFilter = range,
                    dataOriginFilter = setOf(exercise.metadata.dataOrigin),
                ),
            )
            val sourceId = exercise.metadata.id
            ImportedActivityEntity(
                id = "health-connect-$sourceId",
                sourceRecordId = sourceId,
                sourceType = ActivitySourceType.HEALTH_CONNECT,
                sourcePackage = exercise.metadata.dataOrigin.packageName,
                title = exercise.title?.takeIf { it.isNotBlank() } ?: "Тренировка Garmin",
                activityType = exerciseTypeLabel(exercise.exerciseType),
                startAtEpochMillis = exercise.startTime.toEpochMilli(),
                endAtEpochMillis = exercise.endTime.toEpochMilli(),
                durationSeconds = Duration.between(exercise.startTime, exercise.endTime).seconds,
                distanceMeters = aggregate[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                elevationMeters = aggregate[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
                averageHeartRate = aggregate[HeartRateRecord.BPM_AVG]?.toDouble(),
                maxHeartRate = aggregate[HeartRateRecord.BPM_MAX]?.toDouble(),
                importedAtEpochMillis = now,
            )
        }
    }

    private fun exerciseTypeLabel(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Бег"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Ходьба"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Поход"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Велосипед"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Силовая"
        else -> "Активность $type"
    }
}
