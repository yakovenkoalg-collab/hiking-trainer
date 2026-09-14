package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Migration6To7Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-6-7-test"
    @get:Rule val helper = MigrationTestHelper(instrumentation, MountainFormDatabase::class.java)
    @After fun cleanup() { instrumentation.targetContext.deleteDatabase(databaseName) }

    @Test fun historyRemainsUnchangedAndUnknownMetadataStaysUnknown() {
        helper.createDatabase(databaseName, 6).apply {
            execSQL("""INSERT INTO training_sessions
                (id, plannedEpochDay, title, type, phase, objective, durationMinutes, targetRpe, stepsJson,
                status, completedAtEpochMillis, actualRpe, actualDurationSeconds, completionNotes, planVersion)
                VALUES ('done', 20707, 'Дом', 'STRENGTH', 'BASE', '', 60, 4, '[]', 'COMPLETED', 123456, 3, 600, 'original', 1)""")
            execSQL("""INSERT INTO imported_activities
                (id, sourceRecordId, sourceType, sourcePackage, title, activityType, startAtEpochMillis,
                endAtEpochMillis, durationSeconds, status, importedAtEpochMillis, linkedSessionId)
                VALUES ('garmin', 'record', 'HEALTH_CONNECT', 'test', 'Бег', 'RUNNING', 100, 200, 100, 'LINKED', 300, 'done')""")
            close()
        }
        val db = helper.runMigrationsAndValidate(databaseName, 7, true, MountainFormDatabase.MIGRATION_6_7)
        db.query("SELECT actualDurationSeconds, completionNotes, completedAtEpochMillis, performedEpochDay, recordingMode FROM training_sessions WHERE id='done'").use {
            assertTrue(it.moveToFirst()); assertEquals(600, it.getInt(0)); assertEquals("original", it.getString(1))
            assertEquals(123456L, it.getLong(2)); assertTrue(it.isNull(3)); assertEquals("UNKNOWN", it.getString(4))
        }
        db.query("SELECT linkedSessionId, heartRateSource FROM imported_activities WHERE id='garmin'").use {
            assertTrue(it.moveToFirst()); assertEquals("done", it.getString(0)); assertEquals("UNKNOWN", it.getString(1))
        }
        db.close()
    }
}
