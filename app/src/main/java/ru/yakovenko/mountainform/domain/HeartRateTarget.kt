package ru.yakovenko.mountainform.domain

import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity

enum class HeartRateVerdict { WITHIN, PARTLY_ABOVE, ABOVE }

enum class HeartRateBasis { LAPS, AVERAGE }

/**
 * How a recorded activity compares with a planned heart-rate ceiling.
 * The app stores lap and zone summaries, not every sample, so time above the ceiling is an estimate
 * by laps; [definiteSecondsAbove] is the part that is certain from the watch's own zones.
 */
data class HeartRateCompliance(
    val targetMax: Int,
    val averageHeartRate: Double?,
    val basis: HeartRateBasis,
    val assessedSeconds: Long,
    val secondsAboveTarget: Long,
    val lapsAboveTarget: Int,
    val lapsAssessed: Int,
    val definiteSecondsAbove: Long?,
    /** Laps at or below the ceiling that also have a distance: the basis for pace at easy heart rate. */
    val easyPaceSeconds: Long = 0,
    val easyPaceMeters: Double = 0.0,
) {
    val easySeconds: Long get() = assessedSeconds - secondsAboveTarget

    val verdict: HeartRateVerdict
        get() {
            val share = if (assessedSeconds > 0) secondsAboveTarget.toDouble() / assessedSeconds else 0.0
            return when {
                share <= WITHIN_SHARE -> HeartRateVerdict.WITHIN
                share < ABOVE_SHARE -> HeartRateVerdict.PARTLY_ABOVE
                else -> HeartRateVerdict.ABOVE
            }
        }

    private companion object {
        const val WITHIN_SHARE = 0.10
        const val ABOVE_SHARE = 0.50
    }
}

object HeartRateTarget {
    private val json = Json { ignoreUnknownKeys = true }

    /** Shorter laps are stops and leftovers; their pace says little. */
    private const val MIN_PACE_LAP_METERS = 400.0

    /** The ceiling of a session is the highest step ceiling: short efforts above it are planned separately. */
    fun sessionTarget(steps: List<ExerciseStep>): Int? = steps.mapNotNull { it.targetHeartRateMax }.maxOrNull()

    fun sessionTarget(session: TrainingSessionEntity): Int? =
        sessionTarget(runCatching { json.decodeFromString<List<ExerciseStep>>(session.stepsJson) }.getOrDefault(emptyList()))

    /** The ceiling in force today: the latest planned-or-done session up to today, otherwise the next one. */
    fun currentTarget(sessions: List<TrainingSessionEntity>, todayEpochDay: Long): Int? {
        val withTarget = sessions.mapNotNull { session -> sessionTarget(session)?.let { session.plannedEpochDay to it } }
        return withTarget.filter { it.first <= todayEpochDay }.maxByOrNull { it.first }?.second
            ?: withTarget.minByOrNull { it.first }?.second
    }

    fun compliance(activity: ImportedActivityEntity, targetMax: Int): HeartRateCompliance? = compliance(
        targetMax = targetMax,
        averageHeartRate = activity.averageHeartRate,
        durationSeconds = activity.durationSeconds,
        laps = runCatching { json.decodeFromString<List<ActivityLapSummary>>(activity.lapsJson) }.getOrDefault(emptyList()),
        zoneHighBoundaries = runCatching { json.decodeFromString<List<Double>>(activity.heartRateZoneBoundariesJson) }
            .getOrDefault(emptyList()),
        zoneSeconds = runCatching { json.decodeFromString<List<Double>>(activity.timeInHeartRateZonesJson) }
            .getOrDefault(emptyList()),
    )

    fun compliance(
        targetMax: Int,
        averageHeartRate: Double?,
        durationSeconds: Long,
        laps: List<ActivityLapSummary>,
        zoneHighBoundaries: List<Double> = emptyList(),
        zoneSeconds: List<Double> = emptyList(),
    ): HeartRateCompliance? {
        val definite = definiteSecondsAbove(targetMax, zoneHighBoundaries, zoneSeconds)
        val measuredLaps = laps.filter { it.averageHeartRate != null && it.durationSeconds > 0 }
        if (measuredLaps.isNotEmpty()) {
            val above = measuredLaps.filter { requireNotNull(it.averageHeartRate) > targetMax }
            val easyWithDistance = (measuredLaps - above.toSet()).filter { (it.distanceMeters ?: 0.0) >= MIN_PACE_LAP_METERS }
            return HeartRateCompliance(
                targetMax = targetMax,
                averageHeartRate = averageHeartRate,
                basis = HeartRateBasis.LAPS,
                assessedSeconds = measuredLaps.sumOf { it.durationSeconds },
                secondsAboveTarget = above.sumOf { it.durationSeconds },
                lapsAboveTarget = above.size,
                lapsAssessed = measuredLaps.size,
                definiteSecondsAbove = definite,
                easyPaceSeconds = easyWithDistance.sumOf { it.durationSeconds },
                easyPaceMeters = easyWithDistance.sumOf { requireNotNull(it.distanceMeters) },
            )
        }
        if (averageHeartRate == null || durationSeconds <= 0) return null
        return HeartRateCompliance(
            targetMax = targetMax,
            averageHeartRate = averageHeartRate,
            basis = HeartRateBasis.AVERAGE,
            assessedSeconds = durationSeconds,
            secondsAboveTarget = if (averageHeartRate > targetMax) durationSeconds else 0,
            lapsAboveTarget = 0,
            lapsAssessed = 0,
            definiteSecondsAbove = definite,
        )
    }

    /**
     * Garmin writes one more time bucket than boundaries: bucket i lies above boundary i-1.
     * Only buckets that start at or above the ceiling are certain to be above it.
     */
    private fun definiteSecondsAbove(targetMax: Int, highBoundaries: List<Double>, zoneSeconds: List<Double>): Long? {
        if (highBoundaries.isEmpty() || zoneSeconds.size < 2) return null
        return (1 until zoneSeconds.size)
            .filter { index -> highBoundaries.getOrNull(index - 1)?.let { it >= targetMax } == true }
            .sumOf { zoneSeconds[it] }
            .toLong()
    }
}
