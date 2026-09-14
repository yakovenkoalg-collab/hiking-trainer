package ru.yakovenko.mountainform.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.yakovenko.mountainform.domain.*
import java.time.LocalDate

class SessionRecordingTest {
    @Test fun retrospectiveCompletionPreservesMeasuredSetsAndRejectsDuplicateCompletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MountainFormDatabase::class.java).build()
        try {
            val dao = db.dao()
            val repository = MountainFormRepository(dao)
            dao.upsertProfile(UserProfileEntity(age = 41, heightCm = 183, weightKg = 75.0,
                preferredDays = "", currentPhase = "BASE", shoulderRestrictionActive = false,
                kneeObservationActive = false, updatedAtEpochMillis = 1))
            val day = LocalDate.now().minusDays(1).toEpochDay()
            val steps = listOf(ExerciseStep("plank", "Планка", "2 × 40 сек", ""))
            val session = TrainingSessionEntity("test", day, "Дом", "STRENGTH", "BASE", "", 60, 4, Json.encodeToString(steps))
            dao.upsertSession(session)
            val targets = WorkoutPlanCompiler.compile(steps)
            val measured = retrospectiveSetLog(session.id, targets.first(), 100).copy(elapsedSeconds = 40, timingStatus = SetTimingStatus.RECORDED, actualRestSeconds = 50)
            dao.upsertSetLog(measured)
            dao.upsertSetLog(retrospectiveSetLog(session.id, targets.last(), 100).copy(completed = false))
            val details = SessionCompletionDetails(day, "RETROSPECTIVE", targets.map { retrospectiveSetLog(session.id, it, 200) })
            repository.completeSession(session.id, 3, "30 минут фактически", 1800, details)
            assertEquals(measured, dao.getSetLogs().first { it.setIndex == 1 })
            assertNull(dao.getSetLogs().first { it.setIndex == 2 }.actualRestSeconds)
            assertTrue(dao.getStepLogs().single().completed)
            assertEquals(day, dao.getSession(session.id)?.performedEpochDay)
            assertEquals(1800, dao.getSession(session.id)?.actualDurationSeconds)
            val report = Json.decodeFromString<ReportEnvelope>(repository.exportReport())
            assertEquals(day, report.sessions.single().performedEpochDay)
            assertEquals("USER_ENTERED", report.sessions.single().durationStatus)
            val backup = repository.exportBackup()
            val restoredDb = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MountainFormDatabase::class.java).build()
            try {
                val restored = MountainFormRepository(restoredDb.dao())
                restored.applyBackup(restored.previewBackup(backup))
                assertEquals(dao.getSession(session.id), restoredDb.dao().getSession(session.id))
                assertEquals(dao.getSetLogs(), restoredDb.dao().getSetLogs())
            } finally { restoredDb.close() }
            assertTrue(runCatching { repository.completeSession(session.id, 3, "duplicate", 600, details) }.isFailure)
            assertEquals(1800, dao.getSession(session.id)?.actualDurationSeconds)
        } finally { db.close() }
    }

    @Test fun manualSensorSurvivesRepeatedImport() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MountainFormDatabase::class.java).build()
        try {
            val repository = MountainFormRepository(db.dao())
            val activity = ImportedActivityEntity("hr", "id", "HEALTH_CONNECT", "test", "Run", "RUNNING", 1000, 61000, 60, importedAtEpochMillis = 70000)
            repository.upsertImportedActivities(listOf(activity))
            repository.setHeartRateSource(activity.id, "CHEST_USER")
            repository.upsertImportedActivities(listOf(activity.copy(averageHeartRate = 150.0)))
            assertEquals("CHEST_USER", db.dao().getImportedActivity(activity.id)?.heartRateSource)
            assertEquals(150.0, db.dao().getImportedActivity(activity.id)?.averageHeartRate)
        } finally { db.close() }
    }
}
