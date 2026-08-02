package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration3To4Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-3-4-test"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MountainFormDatabase::class.java)

    @After
    fun cleanup() {
        instrumentation.targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesSessionsAndClassifiesExistingTimers() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO training_sessions (
                    id, plannedEpochDay, title, type, phase, objective, durationMinutes, targetRpe,
                    stepsJson, status, completedAtEpochMillis, actualRpe, completionNotes, planVersion,
                    originalEpochDay, rescheduleReason
                ) VALUES (
                    'session', 1, 'Тренировка', 'STRENGTH', 'BASE', 'test', 60, 5,
                    '[]', 'COMPLETED', 1000, 4, '', 1, 1, ''
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_set_logs (
                    sessionId, stepId, roundIndex, setIndex, plannedReps, actualReps, loadKg,
                    actualRpe, rir, pain, painNote, startedAtEpochMillis, completedAtEpochMillis,
                    elapsedSeconds, completed
                ) VALUES ('session', 'timed', 1, 1, NULL, NULL, NULL, NULL, NULL, 0, '', 1, 2, 45, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_set_logs (
                    sessionId, stepId, roundIndex, setIndex, plannedReps, actualReps, loadKg,
                    actualRpe, rir, pain, painNote, startedAtEpochMillis, completedAtEpochMillis,
                    elapsedSeconds, completed
                ) VALUES ('session', 'untimed', 1, 1, 8, 8, NULL, NULL, NULL, 0, '', 1, 2, 0, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MountainFormDatabase.MIGRATION_3_4,
        )

        migrated.query("SELECT actualDurationSeconds FROM training_sessions WHERE id = 'session'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query(
            """
            SELECT stepId, timingStatus, plannedRestSeconds, actualRestSeconds, restSkipped
            FROM session_set_logs ORDER BY stepId
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("timed", cursor.getString(0))
            assertEquals(SetTimingStatus.RECORDED, cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertNull(cursor.getString(3))
            assertEquals(0, cursor.getInt(4))
            assertTrue(cursor.moveToNext())
            assertEquals("untimed", cursor.getString(0))
            assertEquals(SetTimingStatus.NOT_USED, cursor.getString(1))
        }
        migrated.close()
    }
}
