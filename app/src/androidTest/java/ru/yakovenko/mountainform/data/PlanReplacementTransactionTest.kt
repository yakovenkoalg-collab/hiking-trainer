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
import java.time.LocalDate

class PlanReplacementTransactionTest {
    private lateinit var database: MountainFormDatabase
    private lateinit var dao: MountainFormDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MountainFormDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun replacementDeletesOnlyPlannedSessionsAndPreservesCompletedHistory() = runBlocking {
        val planned = session("planned-old", SessionStatus.PLANNED)
        val completed = session("completed-history", SessionStatus.COMPLETED)
        dao.upsertSessions(listOf(planned, completed))
        dao.upsertStepLogs(
            listOf(
                SessionStepLogEntity(planned.id, "step", true, 1),
                SessionStepLogEntity(completed.id, "step", true, 1),
            ),
        )
        dao.upsertSetLogs(
            listOf(
                SessionSetLogEntity(planned.id, "step", 1, 1, completed = true),
                SessionSetLogEntity(completed.id, "step", 1, 1, completed = true),
            ),
        )
        dao.upsertImportedActivity(
            ImportedActivityEntity(
                id = "activity",
                sourceRecordId = "source",
                sourceType = ActivitySourceType.FIT,
                sourcePackage = "test",
                title = "Бег",
                activityType = "Бег",
                startAtEpochMillis = 1,
                endAtEpochMillis = 2,
                durationSeconds = 1,
                linkedSessionId = planned.id,
                status = ActivityLinkStatus.LINKED,
                importedAtEpochMillis = 3,
            ),
        )

        dao.applyPlanChanges(
            sessions = listOf(session("new-plan", SessionStatus.PLANNED)),
            removedPlannedSessionIds = listOf(planned.id, completed.id),
            revision = PlanRevisionEntity("revision", 4, 1, "test", "replace", "{}", true),
            resolvedCheckpoint = null,
        )

        val sessions = dao.getSessions()
        assertEquals(setOf("completed-history", "new-plan"), sessions.mapTo(mutableSetOf()) { it.id })
        assertTrue(sessions.single { it.id == completed.id }.status == SessionStatus.COMPLETED)
        assertFalse(dao.getStepLogs().any { it.sessionId == planned.id })
        assertTrue(dao.getStepLogs().any { it.sessionId == completed.id })
        assertFalse(dao.getSetLogs().any { it.sessionId == planned.id })
        assertTrue(dao.getSetLogs().any { it.sessionId == completed.id })
        val activity = dao.getImportedActivity("activity")!!
        assertNull(activity.linkedSessionId)
        assertEquals(ActivityLinkStatus.UNLINKED, activity.status)
        assertEquals("revision", dao.getRevisions().single().id)
    }

    @Test
    fun oneTrainingSessionCanBeLinkedToSeveralActivities() = runBlocking {
        val target = session("target", SessionStatus.COMPLETED)
        dao.upsertSession(target)
        dao.upsertImportedActivities(listOf(activity("first"), activity("second")))

        dao.linkImportedActivity("first", target.id)
        dao.linkImportedActivity("second", target.id)

        assertEquals(ActivityLinkStatus.LINKED, dao.getImportedActivity("first")?.status)
        assertEquals(ActivityLinkStatus.LINKED, dao.getImportedActivity("second")?.status)
        assertEquals(target.id, dao.getImportedActivity("first")?.linkedSessionId)
        assertEquals(target.id, dao.getImportedActivity("second")?.linkedSessionId)
    }

    @Test
    fun replacingSessionActivitiesUnlinksOnlyRemovedRecord() = runBlocking {
        val target = session("target", SessionStatus.COMPLETED)
        dao.upsertSession(target)
        dao.upsertImportedActivities(listOf(activity("first"), activity("second"), activity("third")))
        dao.linkImportedActivity("first", target.id)
        dao.linkImportedActivity("second", target.id)

        dao.replaceSessionActivities(target.id, listOf("second", "third"))

        assertNull(dao.getImportedActivity("first")?.linkedSessionId)
        assertEquals(ActivityLinkStatus.UNLINKED, dao.getImportedActivity("first")?.status)
        assertEquals(target.id, dao.getImportedActivity("second")?.linkedSessionId)
        assertEquals(target.id, dao.getImportedActivity("third")?.linkedSessionId)
    }

    @Test
    fun skippedTrainingSessionCannotBeLinked() = runBlocking {
        val skipped = session("skipped", SessionStatus.SKIPPED)
        dao.upsertSession(skipped)
        dao.upsertImportedActivity(activity("activity"))

        val error = runCatching { dao.linkImportedActivity("activity", skipped.id) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(ActivityLinkStatus.UNLINKED, dao.getImportedActivity("activity")?.status)
    }

    @Test
    fun corePracticeMarkCanBeUndone() = runBlocking {
        val day = LocalDate.now().toEpochDay()
        dao.upsertPractice(PracticeLogEntity("$day-core-posture", day, "CORE_POSTURE", 10, ""))

        MountainFormRepository(dao).undoCorePractice()

        assertTrue(dao.getPractices().none { it.id == "$day-core-posture" })
    }

    @Test
    fun restoringSkippedSessionPreservesSetHistory() = runBlocking {
        val skipped = session("restore", SessionStatus.SKIPPED).copy(
            completedAtEpochMillis = 42,
            actualRpe = 7,
            actualDurationSeconds = 900,
            completionNotes = "Пропущена",
        )
        dao.upsertSession(skipped)
        dao.upsertSetLog(SessionSetLogEntity(skipped.id, "step", 1, 1, completed = true))

        MountainFormRepository(dao).restoreSkippedSession(skipped.id)

        val restored = requireNotNull(dao.getSession(skipped.id))
        assertEquals(SessionStatus.PLANNED, restored.status)
        assertNull(restored.completedAtEpochMillis)
        assertNull(restored.actualRpe)
        assertEquals(0, restored.actualDurationSeconds)
        assertEquals("", restored.completionNotes)
        assertTrue(dao.getSetLogs().any { it.sessionId == skipped.id })
    }

    @Test
    fun planWithAnEmptyWorkoutIsRejectedBeforeImport() = runBlocking {
        dao.upsertProfile(
            UserProfileEntity(
                age = 41,
                heightCm = 183,
                weightKg = 75.0,
                preferredDays = "вторник, пятница, воскресенье",
                currentPhase = "BASE",
                shoulderRestrictionActive = true,
                kneeObservationActive = true,
                updatedAtEpochMillis = 1,
            ),
        )
        val raw = Json.encodeToString(
            PlanEnvelope(
                planId = "empty",
                author = "test",
                reason = "test",
                generatedAtEpochMillis = 1,
                sessions = listOf(
                    PlanSession(
                        id = "empty-session",
                        plannedEpochDay = LocalDate.now().toEpochDay(),
                        title = "Пустая",
                        type = "RECOVERY",
                        phase = "BASE",
                        objective = "test",
                        durationMinutes = 10,
                        targetRpe = 2,
                        steps = emptyList(),
                    ),
                ),
            ),
        )

        val error = runCatching { MountainFormRepository(dao).previewPlan(raw) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("хотя бы одно упражнение"))
    }

    private fun activity(id: String) = ImportedActivityEntity(
        id = id,
        sourceRecordId = "source-$id",
        sourceType = ActivitySourceType.FIT,
        sourcePackage = "test",
        title = id,
        activityType = "RUNNING",
        startAtEpochMillis = 1,
        endAtEpochMillis = 2,
        durationSeconds = 1,
        importedAtEpochMillis = 3,
    )

    private fun session(id: String, status: String) = TrainingSessionEntity(
        id = id,
        plannedEpochDay = 1,
        title = id,
        type = "RUN",
        phase = "BASE",
        objective = "test",
        durationMinutes = 30,
        targetRpe = 3,
        stepsJson = "[]",
        status = status,
    )
}
