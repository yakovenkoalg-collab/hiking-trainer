package ru.yakovenko.mountainform.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val age: Int,
    val heightCm: Int,
    val weightKg: Double,
    val preferredDays: String,
    val currentPhase: String,
    val shoulderRestrictionActive: Boolean,
    val kneeObservationActive: Boolean,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "goal_events")
data class GoalEventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val targetEpochDay: Long?,
    val distanceKm: Double?,
    val priority: Int,
    val status: String,
    val notes: String,
)

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey val id: String,
    val plannedEpochDay: Long,
    val title: String,
    val type: String,
    val phase: String,
    val objective: String,
    val durationMinutes: Int,
    val targetRpe: Int,
    val stepsJson: String,
    val status: String = SessionStatus.PLANNED,
    val completedAtEpochMillis: Long? = null,
    val actualRpe: Int? = null,
    val completionNotes: String = "",
    val planVersion: Int = 1,
)

@Entity(tableName = "readiness_checks")
data class ReadinessCheckEntity(
    @PrimaryKey val epochDay: Long,
    val sleep: Int,
    val energy: Int,
    val fatigue: Int,
    val soreness: Int,
    val shoulderPain: Int,
    val kneePain: Int,
    val illness: Boolean,
    val notes: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double?,
    val waistCm: Double?,
    val proteinGoalMet: Boolean,
    val produceGoalMet: Boolean,
    val hydrationGoalMet: Boolean,
    val alcoholFree: Boolean,
    val notes: String,
)

@Entity(tableName = "plan_revisions")
data class PlanRevisionEntity(
    @PrimaryKey val id: String,
    val importedAtEpochMillis: Long,
    val schemaVersion: Int,
    val author: String,
    val reason: String,
    val payloadJson: String,
    val applied: Boolean,
)

@Entity(tableName = "practice_logs")
data class PracticeLogEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val type: String,
    val minutes: Int,
    val notes: String,
)

@Serializable
data class ExerciseStep(
    val id: String,
    val title: String,
    val prescription: String,
    val instructions: String,
    val restSeconds: Int = 0,
    val required: Boolean = true,
    val restrictionTags: List<String> = emptyList(),
)

@Serializable
data class PlanEnvelope(
    val schemaVersion: Int = 1,
    val planId: String,
    val author: String,
    val reason: String,
    val generatedAtEpochMillis: Long,
    val sessions: List<PlanSession>,
)

@Serializable
data class PlanSession(
    val id: String,
    val plannedEpochDay: Long,
    val title: String,
    val type: String,
    val phase: String,
    val objective: String,
    val durationMinutes: Int,
    val targetRpe: Int,
    val steps: List<ExerciseStep>,
)

@Serializable
data class ReportEnvelope(
    val schemaVersion: Int = 1,
    val generatedAtEpochMillis: Long,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val profile: ReportProfile,
    val goals: List<ReportGoal>,
    val sessions: List<ReportSession>,
    val readiness: List<ReportReadiness>,
    val bodyMetrics: List<ReportBodyMetric>,
)

@Serializable
data class ReportProfile(
    val age: Int,
    val heightCm: Int,
    val weightKg: Double,
    val currentPhase: String,
    val activeConstraints: List<String>,
)

@Serializable
data class ReportGoal(
    val type: String,
    val title: String,
    val targetEpochDay: Long?,
    val distanceKm: Double?,
    val status: String,
)

@Serializable
data class ReportSession(
    val plannedEpochDay: Long,
    val title: String,
    val type: String,
    val status: String,
    val targetRpe: Int,
    val actualRpe: Int?,
    val notes: String,
)

@Serializable
data class ReportReadiness(
    val epochDay: Long,
    val sleep: Int,
    val energy: Int,
    val fatigue: Int,
    val shoulderPain: Int,
    val kneePain: Int,
    val illness: Boolean,
    val notes: String,
)

@Serializable
data class ReportBodyMetric(
    val epochDay: Long,
    val weightKg: Double?,
    val waistCm: Double?,
)

object SessionStatus {
    const val PLANNED = "PLANNED"
    const val COMPLETED = "COMPLETED"
    const val SKIPPED = "SKIPPED"
}

object GoalType {
    const val MOUNTAIN = "MOUNTAIN"
    const val RUNNING = "RUNNING"
}
