package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class RunningWeek(
    val weekStartEpochDay: Long,
    val runs: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    /** Seconds with a heart-rate assessment, and the part of them at or below the easy ceiling. */
    val assessedSeconds: Long,
    val easySeconds: Long,
    /** Laps at or below the ceiling with a known distance; pace at easy heart rate is their ratio. */
    val easyPaceSeconds: Long = 0,
    val easyPaceMeters: Double = 0.0,
) {
    val easyShare: Double? get() = if (assessedSeconds > 0) easySeconds.toDouble() / assessedSeconds else null
}

object RunningVolume {
    fun isRun(activityType: String): Boolean {
        val type = activityType.trim().uppercase(java.util.Locale.ROOT)
        return "RUN" in type || "БЕГ" in type
    }

    /** Calendar weeks, Monday first, oldest first; the last entry is the current week. */
    fun weeks(
        activities: List<ImportedActivityEntity>,
        sessions: List<TrainingSessionEntity>,
        today: LocalDate,
        count: Int = 8,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<RunningWeek> {
        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = currentMonday.minusWeeks(count - 1L)
        val defaultTarget = HeartRateTarget.currentTarget(sessions, today.toEpochDay())
        val targetBySession = sessions.associate { it.id to HeartRateTarget.sessionTarget(it) }
        val runs = withoutDuplicates(
            activities.filter { isRun(it.activityType) && it.status != ActivityLinkStatus.IGNORED },
        ).groupBy { activity ->
            Instant.ofEpochMilli(activity.startAtEpochMillis).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        return (0 until count).map { offset ->
            val monday = firstMonday.plusWeeks(offset.toLong())
            val weekRuns = runs[monday].orEmpty()
            val assessed = weekRuns.mapNotNull { run ->
                val target = run.linkedSessionId?.let(targetBySession::get) ?: defaultTarget
                target?.let { HeartRateTarget.compliance(run, it) }
            }
            RunningWeek(
                weekStartEpochDay = monday.toEpochDay(),
                runs = weekRuns.size,
                distanceMeters = weekRuns.sumOf { it.distanceMeters ?: 0.0 },
                durationSeconds = weekRuns.sumOf { it.durationSeconds.coerceAtLeast(0) },
                assessedSeconds = assessed.sumOf { it.assessedSeconds },
                easySeconds = assessed.sumOf { it.easySeconds },
                easyPaceSeconds = assessed.sumOf { it.easyPaceSeconds },
                easyPaceMeters = assessed.sumOf { it.easyPaceMeters },
            )
        }
    }

    /** The same run can arrive from Health Connect and from a FIT file; keep the FIT record, it has laps. */
    private fun withoutDuplicates(runs: List<ImportedActivityEntity>): List<ImportedActivityEntity> {
        val kept = mutableListOf<ImportedActivityEntity>()
        runs.sortedBy { it.startAtEpochMillis }.forEach { run ->
            val previous = kept.lastOrNull()
            if (previous != null && overlapsMostly(previous, run)) {
                if (previous.sourceType != ActivitySourceType.FIT && run.sourceType == ActivitySourceType.FIT) {
                    kept[kept.lastIndex] = run
                }
            } else {
                kept += run
            }
        }
        return kept
    }

    private fun overlapsMostly(first: ImportedActivityEntity, second: ImportedActivityEntity): Boolean {
        val overlap = minOf(first.endAtEpochMillis, second.endAtEpochMillis) -
            maxOf(first.startAtEpochMillis, second.startAtEpochMillis)
        val shorter = minOf(
            first.endAtEpochMillis - first.startAtEpochMillis,
            second.endAtEpochMillis - second.startAtEpochMillis,
        )
        return shorter > 0 && overlap >= shorter * 0.8
    }
}
