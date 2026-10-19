package ru.yakovenko.mountainform.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Migration7To8Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "migration-7-8-test"
    @get:Rule val helper = MigrationTestHelper(instrumentation, MountainFormDatabase::class.java)
    @After fun cleanup() { instrumentation.targetContext.deleteDatabase(databaseName) }

    @Test fun linkedActivityKeepsItsLinkAndGetsAnEmptyHistogram() {
        helper.createDatabase(databaseName, 7).apply {
            execSQL("""INSERT INTO imported_activities
                (id, sourceRecordId, sourceType, sourcePackage, title, activityType, startAtEpochMillis,
                endAtEpochMillis, durationSeconds, averageHeartRate, status, importedAtEpochMillis, linkedSessionId, heartRateSource)
                VALUES ('garmin', 'record', 'HEALTH_CONNECT', 'test', 'Бег', 'RUNNING', 100, 200, 100, 148.0, 'LINKED', 300, 'run', 'CHEST_USER')""")
            close()
        }
        val db = helper.runMigrationsAndValidate(databaseName, 8, true, MountainFormDatabase.MIGRATION_7_8)
        db.query("SELECT linkedSessionId, status, heartRateSource, averageHeartRate, heartRateHistogramJson FROM imported_activities WHERE id='garmin'").use {
            assertTrue(it.moveToFirst())
            assertEquals("run", it.getString(0)); assertEquals("LINKED", it.getString(1)); assertEquals("CHEST_USER", it.getString(2))
            assertEquals(148.0, it.getDouble(3), 0.0); assertEquals("{}", it.getString(4))
        }
        db.close()
    }
}
