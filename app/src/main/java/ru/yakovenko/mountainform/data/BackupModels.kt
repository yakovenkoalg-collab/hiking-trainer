package ru.yakovenko.mountainform.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = 3,
    val generatedAtEpochMillis: Long,
    val profile: UserProfileEntity,
    val goals: List<GoalEventEntity>,
    val sessions: List<TrainingSessionEntity>,
    val readiness: List<ReadinessCheckEntity>,
    val bodyMetrics: List<BodyMetricEntity>,
    val revisions: List<PlanRevisionEntity>,
    val practices: List<PracticeLogEntity>,
    val rescheduleEvents: List<RescheduleEventEntity>,
    val stepLogs: List<SessionStepLogEntity>,
    val postureAssessments: List<PostureAssessmentEntity>,
    val preferences: AppSettingsEntity,
    val setLogs: List<SessionSetLogEntity> = emptyList(),
    val reviewCheckpoints: List<ReviewCheckpointEntity> = emptyList(),
    val importedActivities: List<ImportedActivityEntity> = emptyList(),
)

data class BackupPreview(
    val backup: BackupEnvelope,
    val newSessions: Int,
    val restoredHistory: Int,
    val preservedLocalHistory: Int,
    val readinessRecords: Int,
    val bodyMetricRecords: Int,
)
