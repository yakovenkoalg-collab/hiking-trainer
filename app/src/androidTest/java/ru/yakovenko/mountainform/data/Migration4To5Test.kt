package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration4To5Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-4-5-test"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MountainFormDatabase::class.java)

    @After
    fun cleanup() {
        instrumentation.targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesImportedActivityAndAddsDetailedFitMetrics() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO imported_activities (
                    id, sourceRecordId, sourceType, sourcePackage, title, activityType,
                    startAtEpochMillis, endAtEpochMillis, durationSeconds, distanceMeters,
                    elevationMeters, caloriesKcal, averageHeartRate, maxHeartRate,
                    averageCadence, averagePowerWatts, linkedSessionId, status,
                    importedAtEpochMillis, rawFileName
                ) VALUES (
                    'fit-1', '1', 'FIT', 'com.garmin.fit', 'Бег', 'Бег',
                    1000, 2000, 1000, 2450, 12, 200, 150, 170,
                    169, 250, 'session-1', 'LINKED', 3000, 'run.fit'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            MountainFormDatabase.MIGRATION_4_5,
        )

        migrated.query(
            """
            SELECT distanceMeters, averageCadence, linkedSessionId, descentMeters,
                   aerobicTrainingEffect, heartRateZoneBoundariesJson,
                   timeInHeartRateZonesJson, lapsJson
            FROM imported_activities WHERE id = 'fit-1'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2450.0, cursor.getDouble(0), 0.01)
            assertEquals(169.0, cursor.getDouble(1), 0.01)
            assertEquals("session-1", cursor.getString(2))
            assertNull(cursor.getString(3))
            assertNull(cursor.getString(4))
            assertEquals("[]", cursor.getString(5))
            assertEquals("[]", cursor.getString(6))
            assertEquals("[]", cursor.getString(7))
        }
        migrated.close()
    }
}
