package ru.yakovenko.mountainform.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MountainFormDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM goal_events ORDER BY priority DESC, targetEpochDay ASC")
    fun observeGoals(): Flow<List<GoalEventEntity>>

    @Query("SELECT * FROM goal_events ORDER BY priority DESC, targetEpochDay ASC")
    suspend fun getGoals(): List<GoalEventEntity>

    @Upsert
    suspend fun upsertGoal(goal: GoalEventEntity)

    @Upsert
    suspend fun upsertGoals(goals: List<GoalEventEntity>)

    @Query("SELECT * FROM training_sessions ORDER BY plannedEpochDay ASC")
    fun observeSessions(): Flow<List<TrainingSessionEntity>>

    @Query("SELECT * FROM training_sessions ORDER BY plannedEpochDay ASC")
    suspend fun getSessions(): List<TrainingSessionEntity>

    @Query("SELECT * FROM training_sessions WHERE id = :id")
    suspend fun getSession(id: String): TrainingSessionEntity?

    @Upsert
    suspend fun upsertSession(session: TrainingSessionEntity)

    @Upsert
    suspend fun upsertSessions(sessions: List<TrainingSessionEntity>)

    @Query("SELECT * FROM readiness_checks ORDER BY epochDay DESC")
    fun observeReadiness(): Flow<List<ReadinessCheckEntity>>

    @Query("SELECT * FROM readiness_checks ORDER BY epochDay DESC")
    suspend fun getReadiness(): List<ReadinessCheckEntity>

    @Upsert
    suspend fun upsertReadiness(check: ReadinessCheckEntity)

    @Upsert
    suspend fun upsertReadinessChecks(checks: List<ReadinessCheckEntity>)

    @Query("SELECT * FROM body_metrics ORDER BY epochDay DESC")
    fun observeBodyMetrics(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY epochDay DESC")
    suspend fun getBodyMetrics(): List<BodyMetricEntity>

    @Upsert
    suspend fun upsertBodyMetric(metric: BodyMetricEntity)

    @Upsert
    suspend fun upsertBodyMetrics(metrics: List<BodyMetricEntity>)

    @Upsert
    suspend fun upsertRevision(revision: PlanRevisionEntity)

    @Upsert
    suspend fun upsertRevisions(revisions: List<PlanRevisionEntity>)

    @Query("SELECT * FROM plan_revisions ORDER BY importedAtEpochMillis DESC")
    suspend fun getRevisions(): List<PlanRevisionEntity>

    @Query("SELECT * FROM practice_logs ORDER BY epochDay DESC")
    fun observePractices(): Flow<List<PracticeLogEntity>>

    @Query("SELECT * FROM practice_logs ORDER BY epochDay DESC")
    suspend fun getPractices(): List<PracticeLogEntity>

    @Upsert
    suspend fun upsertPractice(practice: PracticeLogEntity)

    @Upsert
    suspend fun upsertPractices(practices: List<PracticeLogEntity>)

    @Query("SELECT * FROM exercise_catalog ORDER BY category, title")
    fun observeExerciseCatalog(): Flow<List<ExerciseCatalogEntity>>

    @Query("SELECT * FROM exercise_catalog ORDER BY category, title")
    suspend fun getExerciseCatalog(): List<ExerciseCatalogEntity>

    @Upsert
    suspend fun upsertExerciseCatalog(exercises: List<ExerciseCatalogEntity>)

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM reschedule_events ORDER BY createdAtEpochMillis DESC")
    fun observeRescheduleEvents(): Flow<List<RescheduleEventEntity>>

    @Query("SELECT * FROM reschedule_events ORDER BY createdAtEpochMillis DESC")
    suspend fun getRescheduleEvents(): List<RescheduleEventEntity>

    @Upsert
    suspend fun upsertRescheduleEvent(event: RescheduleEventEntity)

    @Upsert
    suspend fun upsertRescheduleEvents(events: List<RescheduleEventEntity>)

    @Transaction
    suspend fun rescheduleSession(session: TrainingSessionEntity, event: RescheduleEventEntity) {
        upsertSession(session)
        upsertRescheduleEvent(event)
    }

    @Query("SELECT * FROM session_step_logs WHERE sessionId = :sessionId")
    fun observeStepLogs(sessionId: String): Flow<List<SessionStepLogEntity>>

    @Query("SELECT * FROM session_step_logs")
    fun observeStepLogs(): Flow<List<SessionStepLogEntity>>

    @Query("SELECT * FROM session_step_logs")
    suspend fun getStepLogs(): List<SessionStepLogEntity>

    @Upsert
    suspend fun upsertStepLog(log: SessionStepLogEntity)

    @Upsert
    suspend fun upsertStepLogs(logs: List<SessionStepLogEntity>)

    @Query("SELECT * FROM posture_assessments ORDER BY epochDay DESC, createdAtEpochMillis DESC")
    fun observePostureAssessments(): Flow<List<PostureAssessmentEntity>>

    @Query("SELECT * FROM posture_assessments ORDER BY epochDay DESC, createdAtEpochMillis DESC")
    suspend fun getPostureAssessments(): List<PostureAssessmentEntity>

    @Upsert
    suspend fun upsertPostureAssessment(assessment: PostureAssessmentEntity)

    @Upsert
    suspend fun upsertPostureAssessments(assessments: List<PostureAssessmentEntity>)

    @Transaction
    suspend fun mergeBackup(
        profile: UserProfileEntity,
        goals: List<GoalEventEntity>,
        sessions: List<TrainingSessionEntity>,
        readiness: List<ReadinessCheckEntity>,
        bodyMetrics: List<BodyMetricEntity>,
        revisions: List<PlanRevisionEntity>,
        practices: List<PracticeLogEntity>,
        rescheduleEvents: List<RescheduleEventEntity>,
        stepLogs: List<SessionStepLogEntity>,
        postureAssessments: List<PostureAssessmentEntity>,
        settings: AppSettingsEntity,
    ) {
        upsertProfile(profile)
        upsertGoals(goals)
        upsertSessions(sessions)
        upsertReadinessChecks(readiness)
        upsertBodyMetrics(bodyMetrics)
        upsertRevisions(revisions)
        upsertPractices(practices)
        upsertRescheduleEvents(rescheduleEvents)
        upsertStepLogs(stepLogs)
        upsertPostureAssessments(postureAssessments)
        upsertSettings(settings)
    }

    @Query("SELECT COUNT(*) FROM training_sessions")
    suspend fun sessionCount(): Int
}

@Database(
    entities = [
        UserProfileEntity::class,
        GoalEventEntity::class,
        TrainingSessionEntity::class,
        ReadinessCheckEntity::class,
        BodyMetricEntity::class,
        PlanRevisionEntity::class,
        PracticeLogEntity::class,
        ExerciseCatalogEntity::class,
        AppSettingsEntity::class,
        RescheduleEventEntity::class,
        SessionStepLogEntity::class,
        PostureAssessmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MountainFormDatabase : RoomDatabase() {
    abstract fun dao(): MountainFormDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE training_sessions ADD COLUMN originalEpochDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE training_sessions ADD COLUMN rescheduleReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE training_sessions SET originalEpochDay = plannedEpochDay WHERE originalEpochDay = 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercise_catalog (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        setup TEXT NOT NULL,
                        execution TEXT NOT NULL,
                        breathing TEXT NOT NULL,
                        commonMistakesJson TEXT NOT NULL,
                        restrictionTagsJson TEXT NOT NULL,
                        illustrationKey TEXT NOT NULL,
                        frameCount INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER NOT NULL PRIMARY KEY,
                        sharedFolderUri TEXT,
                        sharedFolderName TEXT,
                        automaticSync INTEGER NOT NULL,
                        lastSyncAtEpochMillis INTEGER,
                        lastSyncMessage TEXT NOT NULL,
                        remindersEnabled INTEGER NOT NULL,
                        reminderHour INTEGER NOT NULL,
                        reminderMinute INTEGER NOT NULL,
                        healthWindowDays INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reschedule_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        fromEpochDay INTEGER NOT NULL,
                        toEpochDay INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS session_step_logs (
                        sessionId TEXT NOT NULL,
                        stepId TEXT NOT NULL,
                        completed INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(sessionId, stepId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS posture_assessments (
                        id TEXT NOT NULL PRIMARY KEY,
                        epochDay INTEGER NOT NULL,
                        frontPhotoUri TEXT,
                        sidePhotoUri TEXT,
                        backPhotoUri TEXT,
                        selfRating INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): MountainFormDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MountainFormDatabase::class.java,
                "mountain-form.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}
