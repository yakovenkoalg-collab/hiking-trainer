package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Migration5To6Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-5-6-test"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MountainFormDatabase::class.java)

    @After
    fun cleanup() {
        instrumentation.targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesProfileAndStartsWithRestrictedShoulderLoad() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO user_profile (
                    id, age, heightCm, weightKg, preferredDays, currentPhase,
                    shoulderRestrictionActive, kneeObservationActive, updatedAtEpochMillis
                ) VALUES (1, 41, 183, 75, 'вт/пт/вс', 'BASE', 1, 1, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            MountainFormDatabase.MIGRATION_5_6,
        )

        migrated.query("SELECT age, shoulderRestrictionActive, shoulderLoadPhase FROM user_profile WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(41, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(ShoulderLoadPhase.RESTRICTED, cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    fun migrationMarksShoulderAsFullyAvailableWhenRestrictionWasInactive() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO user_profile (
                    id, age, heightCm, weightKg, preferredDays, currentPhase,
                    shoulderRestrictionActive, kneeObservationActive, updatedAtEpochMillis
                ) VALUES (1, 41, 183, 75, 'вт/пт/вс', 'BASE', 0, 1, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            MountainFormDatabase.MIGRATION_5_6,
        )

        migrated.query("SELECT shoulderLoadPhase FROM user_profile WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(ShoulderLoadPhase.FULL, cursor.getString(0))
        }
        migrated.close()
    }
}
