package ru.yakovenko.mountainform.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.LocalDate

class ActivitiesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun refreshAndLinkAreAvailableOnTheSameScreen() {
        var refreshes = 0
        var linkedActivity: String? = null
        var linkedSession: String? = null
        val today = LocalDate.now().toEpochDay()
        val activity = ImportedActivityEntity(
            id = "garmin-bike",
            sourceRecordId = "garmin-bike-record",
            sourceType = ActivitySourceType.HEALTH_CONNECT,
            sourcePackage = "com.garmin.android.apps.connectmobile",
            title = "Велосипед",
            activityType = "BIKING",
            startAtEpochMillis = System.currentTimeMillis(),
            endAtEpochMillis = System.currentTimeMillis() + 3_600_000,
            durationSeconds = 3_600,
            distanceMeters = 20_000.0,
            importedAtEpochMillis = System.currentTimeMillis(),
        )
        val session = TrainingSessionEntity(
            id = "friday",
            plannedEpochDay = today,
            title = "Пятничная тренировка",
            type = "AEROBIC",
            phase = "BASE",
            objective = "Велосипед и ноги",
            durationMinutes = 75,
            targetRpe = 5,
            stepsJson = "[]",
        )

        composeRule.setContent {
            MountainFormTheme {
                ActivitiesScreen(
                    activities = listOf(activity),
                    sessions = listOf(session),
                    healthSummary = HealthSummary(
                        available = true,
                        permissionsGranted = true,
                        workouts = 1,
                        distanceKm = 20.0,
                        dataOrigins = setOf("com.garmin.android.apps.connectmobile"),
                    ),
                    onBack = {},
                    onRequestPermissions = {},
                    onRefresh = { refreshes++ },
                    onWindowChange = {},
                    onShowPrivacy = {},
                    onImportFit = {},
                    onLinkActivity = { activityId, sessionId ->
                        linkedActivity = activityId
                        linkedSession = sessionId
                    },
                    onIgnoreActivity = {},
                )
            }
        }

        composeRule.onNodeWithText("Тренировки Garmin").assertIsDisplayed()
        composeRule.onNodeWithText("Обновить данные").performClick()
        assertEquals(1, refreshes)

        composeRule.onNodeWithText("Велосипед").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Связать").performScrollTo().performClick()
        composeRule.onNodeWithText("Связать с тренировкой").assertIsDisplayed()
        composeRule.onNodeWithText("Пятничная тренировка", substring = true).performClick()

        assertEquals("garmin-bike", linkedActivity)
        assertEquals("friday", linkedSession)
    }
}
