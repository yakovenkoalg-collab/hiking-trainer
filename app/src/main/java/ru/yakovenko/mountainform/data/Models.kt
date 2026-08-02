package ru.yakovenko.mountainform.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
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

@Serializable
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
    @ColumnInfo(defaultValue = "0") val originalEpochDay: Long = plannedEpochDay,
    @ColumnInfo(defaultValue = "''") val rescheduleReason: String = "",
)

@Serializable
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

@Serializable
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

@Serializable
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

@Serializable
@Entity(tableName = "practice_logs")
data class PracticeLogEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val type: String,
    val minutes: Int,
    val notes: String,
)

@Serializable
@Entity(tableName = "exercise_catalog")
data class ExerciseCatalogEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val setup: String,
    val execution: String,
    val breathing: String,
    val commonMistakesJson: String,
    val restrictionTagsJson: String,
    val illustrationKey: String,
    val frameCount: Int,
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val sharedFolderUri: String? = null,
    val sharedFolderName: String? = null,
    val automaticSync: Boolean = false,
    val lastSyncAtEpochMillis: Long? = null,
    val lastSyncMessage: String = "Общая папка не выбрана",
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val healthWindowDays: Int = 30,
)

@Serializable
@Entity(tableName = "reschedule_events")
data class RescheduleEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val reason: String,
    val createdAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "session_step_logs", primaryKeys = ["sessionId", "stepId"])
data class SessionStepLogEntity(
    val sessionId: String,
    val stepId: String,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)

@Serializable
@Entity(tableName = "posture_assessments")
data class PostureAssessmentEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val frontPhotoUri: String? = null,
    val sidePhotoUri: String? = null,
    val backPhotoUri: String? = null,
    val selfRating: Int,
    val notes: String,
    val createdAtEpochMillis: Long,
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
    val exerciseId: String = "",
    val illustrationKey: String = "",
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
