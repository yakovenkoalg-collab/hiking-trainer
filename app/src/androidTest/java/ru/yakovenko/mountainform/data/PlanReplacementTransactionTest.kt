package ru.yakovenko.mountainform.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
