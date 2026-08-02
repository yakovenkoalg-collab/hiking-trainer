package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration2To3Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-2-3-test"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MountainFormDatabase::class.java)

    @After
    fun cleanup() {
        instrumentation.targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migrationAddsWorkoutGarminAndReviewDataWithoutChangingSettings() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO app_settings (
                    id, sharedFolderUri, sharedFolderName, automaticSync, lastSyncAtEpochMillis,
                    lastSyncMessage, remindersEnabled, reminderHour, reminderMinute, healthWindowDays
                ) VALUES (1, NULL, NULL, 0, NULL, 'Сохранено', 1, 18, 30, 30)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            MountainFormDatabase.MIGRATION_2_3,
        )

        migrated.query(
            "SELECT lastSyncMessage, remindersEnabled, yandexSyncEnabled, yandexRootPath FROM app_settings WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Сохранено", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals("disk:/Горная форма", cursor.getString(3))
        }
        listOf("session_set_logs", "review_checkpoints", "imported_activities").forEach { table ->
            assertTrue(tableExists(migrated, table))
        }
        migrated.close()
    }

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
            it.moveToFirst()
        }
}
