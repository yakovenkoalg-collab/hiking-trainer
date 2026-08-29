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
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.LocalDate
import java.time.ZoneId

class ActivitiesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun refreshAndLinkAreAvailableOnTheSameScreen() {
        var refreshes = 0
        var linkedActivity: String? = null
        var linkedSession: String? = null
        val today = LocalDate.now().toEpochDay()
        val activityStart = System.currentTimeMillis() - 3_600_000
        val activity = ImportedActivityEntity(
            id = "garmin-bike",
            sourceRecordId = "garmin-bike-record",
            sourceType = ActivitySourceType.HEALTH_CONNECT,
            sourcePackage = "com.garmin.android.apps.connectmobile",
            title = "Велосипед",
            activityType = "BIKING",
            startAtEpochMillis = activityStart,
            endAtEpochMillis = activityStart + 3_600_000,
            durationSeconds = 3_600,
            distanceMeters = 20_000.0,
            averageCadence = 84.0,
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
        val alreadyLinkedSession = session.copy(id = "linked", title = "Уже связанная")
        val outsideWindowSession = session.copy(
            id = "outside",
            plannedEpochDay = today - 8,
            title = "Слишком старая",
        )
        val earlierSession = session.copy(
            id = "earlier",
            plannedEpochDay = today - 2,
            title = "Ранняя тренировка",
        )
        val laterSession = session.copy(
            id = "later",
            plannedEpochDay = today + 2,
            title = "Поздняя тренировка",
        )
        val boundarySession = session.copy(
            id = "boundary",
            plannedEpochDay = today + 7,
            title = "Граница окна",
        )
        val skippedSession = session.copy(
            id = "skipped",
            plannedEpochDay = today + 1,
            title = "Пропущенная",
            status = SessionStatus.SKIPPED,
        )
        val otherActivity = activity.copy(
            id = "other-activity",
            sourceRecordId = "other-record",
            title = "Другая активность",
            activityType = "RUNNING",
            linkedSessionId = alreadyLinkedSession.id,
            status = ActivityLinkStatus.LINKED,
        )
        val sessions = listOf(
            outsideWindowSession,
            boundarySession,
            skippedSession,
            laterSession,
            alreadyLinkedSession,
            session,
            earlierSession,
        )
        assertEquals(
            listOf("earlier", "friday", "linked", "later", "boundary"),
            linkCandidates(activity, sessions).map { it.id },
        )

        composeRule.setContent {
            MountainFormTheme {
                ActivitiesScreen(
                    activities = listOf(activity, otherActivity),
                    sessions = sessions,
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
                    onRestoreActivity = {},
                )
            }
        }

        composeRule.onNodeWithText("Тренировки Garmin").assertIsDisplayed()
        composeRule.onNodeWithText("Обновить данные").performClick()
        assertEquals(1, refreshes)

        composeRule.onNodeWithText("Велосипед").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Тип: Велосипед").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("20.0 км/ч", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("каденс 84 об/мин", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Связать").performScrollTo().performClick()
        composeRule.onNodeWithText("Связать с тренировкой").assertIsDisplayed()
        composeRule.onNodeWithText("Тренировки за 7 дней до и после активности").assertIsDisplayed()
        composeRule.onNodeWithText("Пятничная тренировка", substring = true).performClick()

        assertEquals("garmin-bike", linkedActivity)
        assertEquals("friday", linkedSession)
    }

    @Test
    fun ignoredActivityCanBeReturnedToLinking() {
        var restoredActivity: String? = null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ignored = ImportedActivityEntity(
            id = "ignored-run",
            sourceRecordId = "ignored-run-record",
            sourceType = ActivitySourceType.FIT,
            sourcePackage = "fit",
            title = "Утренний бег",
            activityType = "RUNNING",
            startAtEpochMillis = start,
            endAtEpochMillis = start + 1_800_000,
            durationSeconds = 1_800,
            status = ActivityLinkStatus.IGNORED,
            importedAtEpochMillis = start,
        )

        composeRule.setContent {
            MountainFormTheme {
                ActivitiesScreen(
                    activities = listOf(ignored),
                    sessions = emptyList(),
                    healthSummary = HealthSummary(available = true, permissionsGranted = true),
                    onBack = {},
                    onRequestPermissions = {},
                    onRefresh = {},
                    onWindowChange = {},
                    onShowPrivacy = {},
                    onImportFit = {},
                    onLinkActivity = { _, _ -> },
                    onIgnoreActivity = {},
                    onRestoreActivity = { restoredActivity = it },
                )
            }
        }

        composeRule.onNodeWithText("Вернуть к привязке").performScrollTo().performClick()
        assertEquals("ignored-run", restoredActivity)
    }
}
