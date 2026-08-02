package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration1To2Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-1-2-test"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation,
        MountainFormDatabase::class.java,
    )

    @After
    fun cleanup() {
        instrumentation.targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesSessionsAndAddsV2Tables() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO training_sessions (
                    id, plannedEpochDay, title, type, phase, objective, durationMinutes,
                    targetRpe, stepsJson, status, completedAtEpochMillis, actualRpe,
                    completionNotes, planVersion
                ) VALUES ('kept', 123, 'Session', 'RECOVERY', 'phase', 'objective', 60, 3,
                    '[]', 'PLANNED', NULL, NULL, '', 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            MountainFormDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT plannedEpochDay, originalEpochDay, rescheduleReason FROM training_sessions WHERE id = 'kept'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(123L, cursor.getLong(0))
            assertEquals(123L, cursor.getLong(1))
            assertEquals("", cursor.getString(2))
        }
        assertTrue(tableExists(migrated, "exercise_catalog"))
        assertTrue(tableExists(migrated, "app_settings"))
        assertTrue(tableExists(migrated, "reschedule_events"))
        assertTrue(tableExists(migrated, "session_step_logs"))
        assertTrue(tableExists(migrated, "posture_assessments"))
        migrated.close()
    }

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
            it.moveToFirst()
        }
}
