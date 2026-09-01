package ru.yakovenko.mountainform.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.AppSettingsEntity
import ru.yakovenko.mountainform.data.UserProfileEntity
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.DataSyncState
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme

class SettingsSafetyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileCannotSaveImplausibleAge() {
        val profile = UserProfileEntity(
            age = 41,
            heightCm = 183,
            weightKg = 75.0,
            preferredDays = "Вторник, пятница, воскресенье",
            currentPhase = "BASE",
            shoulderRestrictionActive = true,
            kneeObservationActive = true,
            updatedAtEpochMillis = 0,
        )
        composeRule.setContent {
            MountainFormTheme {
                ProfileSettingsScreen(AppUiState(profile = profile), {}, {}, {})
            }
        }

        val ageField = composeRule.onNode(hasText("Возраст", substring = true) and hasSetTextAction())
        ageField.performTextClearance()
        ageField.performTextInput("10")

        composeRule.onNodeWithText("18–100").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Сохранить"))
        composeRule.onNodeWithText("Сохранить").assertIsNotEnabled()
    }

    @Test
    fun yandexDisconnectRequiresConfirmation() {
        var disconnected = false
        composeRule.setContent {
            MountainFormTheme {
                DataSyncScreen(
                    settings = AppSettingsEntity(yandexSyncEnabled = true, yandexAccountLabel = "test"),
                    onBack = {},
                    onSelectFolder = {},
                    onUpdateSettings = {},
                    onSync = {},
                    onCreateBackup = {},
                    onRestoreBackup = {},
                    yandexConnected = true,
                    yandexLoginConfigured = true,
                    syncState = DataSyncState(),
                    onConnectYandex = {},
                    onDisconnectYandex = { disconnected = true },
                    onSyncYandex = {},
                    onCreateYandexBackup = {},
                    onShareReport = {},
                )
            }
        }

        composeRule.onNodeWithText("Отключить").performClick()

        composeRule.onNodeWithText("Отключить Яндекс Диск?").assertIsDisplayed()
        composeRule.runOnIdle { check(!disconnected) }
    }

    @Test
    fun yandexSyncShowsProgressAndPreventsDuplicateStart() {
        composeRule.setContent {
            MountainFormTheme {
                DataSyncScreen(
                    settings = AppSettingsEntity(yandexSyncEnabled = true, yandexAccountLabel = "test"),
                    onBack = {},
                    onSelectFolder = {},
                    onUpdateSettings = {},
                    onSync = {},
                    onCreateBackup = {},
                    onRestoreBackup = {},
                    yandexConnected = true,
                    yandexLoginConfigured = true,
                    syncState = DataSyncState(
                        running = true,
                        stage = "Загружаем актуальный отчёт…",
                        transferredBytes = 10_240,
                        totalBytes = 20_480,
                    ),
                    onConnectYandex = {},
                    onDisconnectYandex = {},
                    onSyncYandex = {},
                    onCreateYandexBackup = {},
                    onShareReport = {},
                )
            }
        }

        composeRule.onNodeWithText("Загружаем актуальный отчёт…").assertIsDisplayed()
        composeRule.onNodeWithText("50%", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Выполняется…", substring = true).assertIsNotEnabled()
    }
}
