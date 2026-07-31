package ru.yakovenko.mountainform.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
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

    @Query("SELECT * FROM training_sessions ORDER BY plannedEpochDay ASC")
    fun observeSessions(): Flow<List<TrainingSessionEntity>>

    @Query("SELECT * FROM training_sessions ORDER BY plannedEpochDay ASC")
    suspend fun getSessions(): List<TrainingSessionEntity>

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

    @Query("SELECT * FROM body_metrics ORDER BY epochDay DESC")
    fun observeBodyMetrics(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY epochDay DESC")
    suspend fun getBodyMetrics(): List<BodyMetricEntity>

    @Upsert
    suspend fun upsertBodyMetric(metric: BodyMetricEntity)

    @Upsert
    suspend fun upsertRevision(revision: PlanRevisionEntity)

    @Query("SELECT * FROM practice_logs ORDER BY epochDay DESC")
    fun observePractices(): Flow<List<PracticeLogEntity>>

    @Upsert
    suspend fun upsertPractice(practice: PracticeLogEntity)

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
    ],
    version = 1,
    exportSchema = true,
)
abstract class MountainFormDatabase : RoomDatabase() {
    abstract fun dao(): MountainFormDao

    companion object {
        fun create(context: Context): MountainFormDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MountainFormDatabase::class.java,
                "mountain-form.db",
            ).build()
    }
}
