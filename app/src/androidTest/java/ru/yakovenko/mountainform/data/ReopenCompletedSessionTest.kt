package ru.yakovenko.mountainform.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReopenCompletedSessionTest {
    private lateinit var database: MountainFormDatabase
    private lateinit var dao: MountainFormDao
    private lateinit var repository: MountainFormRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MountainFormDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao()
        repository = MountainFormRepository(dao)
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun startOverClearsWorkoutProgressAndOpenCheckpointButPreservesGarminLink() = runBlocking {
        val now = 10_000L
        val session = completedSession("accidental", now - 1_000)
        dao.upsertSession(session)
        dao.upsertStepLogs(
            listOf(
                SessionStepLogEntity(session.id, "first", true, 100),
                SessionStepLogEntity(session.id, "second", true, 200),
            ),
        )
        dao.upsertSetLogs(
            listOf(
                completedSet(session.id, "first", 100),
                completedSet(session.id, "second", 200),
            ),
        )
        dao.upsertImportedActivity(linkedActivity("garmin", session.id))
        dao.upsertReviewCheckpoints(
            listOf(
                checkpoint("open", session.id, ReviewStatus.EXPORTED),
                checkpoint("resolved", session.id, ReviewStatus.RESOLVED),
            ),
        )

        repository.reopenCompletedSession(session.id, ReopenCompletedMode.START_OVER, now)

        val reopened = requireNotNull(dao.getSession(session.id))
        assertEquals(SessionStatus.PLANNED, reopened.status)
        assertNull(reopened.completedAtEpochMillis)
        assertNull(reopened.actualRpe)
        assertEquals(0, reopened.actualDurationSeconds)
        assertEquals("", reopened.completionNotes)
        assertFalse(dao.getStepLogs().any { it.sessionId == session.id })
        assertFalse(dao.getSetLogs().any { it.sessionId == session.id })
        assertEquals(session.id, dao.getImportedActivity("garmin")?.linkedSessionId)
        assertEquals(ActivityLinkStatus.LINKED, dao.getImportedActivity("garmin")?.status)
        assertEquals(listOf("resolved"), dao.getReviewCheckpoints().map { it.id })
    }

    @Test
    fun continueKeepsEarlierProgressAndReopensTheLastCompletedStage() = runBlocking {
        val now = 20_000L
        val session = completedSession("continue", now - 1_000)
        dao.upsertSession(session)
        dao.upsertStepLogs(
            listOf(
                SessionStepLogEntity(session.id, "first", true, 100),
                SessionStepLogEntity(session.id, "last", true, 200),
            ),
        )
        dao.upsertSetLogs(
            listOf(
                completedSet(session.id, "first", 100),
                completedSet(session.id, "last", 200),
            ),
        )

        repository.reopenCompletedSession(session.id, ReopenCompletedMode.CONTINUE, now)

        val reopened = requireNotNull(dao.getSession(session.id))
        assertEquals(SessionStatus.PLANNED, reopened.status)
        assertEquals(3_600, reopened.actualDurationSeconds)
        assertEquals(listOf("first"), dao.getStepLogs().filter { it.sessionId == session.id }.map { it.stepId })
        assertEquals(listOf("first"), dao.getSetLogs().filter { it.sessionId == session.id }.map { it.stepId })
    }

    @Test
    fun completedWorkoutOlderThanWindowCannotBeReopened() = runBlocking {
        val now = COMPLETED_REOPEN_WINDOW_MILLIS + 10_000L
        val session = completedSession("old", 1)
        dao.upsertSession(session)

        val failure = runCatching {
            repository.reopenCompletedSession(session.id, ReopenCompletedMode.START_OVER, now)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(SessionStatus.COMPLETED, dao.getSession(session.id)?.status)
    }

    private fun completedSession(id: String, completedAt: Long) = TrainingSessionEntity(
        id = id,
        plannedEpochDay = 1,
        title = "Тренировка",
        type = "HYBRID",
        phase = "BASE",
        objective = "Проверка",
        durationMinutes = 60,
        targetRpe = 5,
        stepsJson = "[]",
        status = SessionStatus.COMPLETED,
        completedAtEpochMillis = completedAt,
        actualRpe = 2,
        actualDurationSeconds = 3_600,
        completionNotes = "Случайное завершение",
    )

    private fun completedSet(sessionId: String, stepId: String, completedAt: Long) = SessionSetLogEntity(
        sessionId = sessionId,
        stepId = stepId,
        roundIndex = 1,
        setIndex = 1,
        completedAtEpochMillis = completedAt,
        completed = true,
    )

    private fun checkpoint(id: String, sessionId: String, status: String) = ReviewCheckpointEntity(
        id = id,
        createdAtEpochMillis = 1,
        completedSessionIdsJson = Json.encodeToString(listOf(sessionId)),
        reason = "Завершены три ключевые тренировки",
        status = status,
    )

    private fun linkedActivity(id: String, sessionId: String) = ImportedActivityEntity(
        id = id,
        sourceRecordId = id,
        sourceType = ActivitySourceType.HEALTH_CONNECT,
        sourcePackage = "com.garmin.android.apps.connectmobile",
        title = "Тренировка Garmin",
        activityType = "Бег",
        startAtEpochMillis = 1,
        endAtEpochMillis = 2,
        durationSeconds = 1,
        linkedSessionId = sessionId,
        status = ActivityLinkStatus.LINKED,
        importedAtEpochMillis = 3,
    )
}
