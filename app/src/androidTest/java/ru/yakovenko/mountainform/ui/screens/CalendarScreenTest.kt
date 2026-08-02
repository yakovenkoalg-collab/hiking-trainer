package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.LocalDate

class CalendarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsSelectedDaySessionAndCalendarControls() {
        val today = LocalDate.now().toEpochDay()
        val state = AppUiState(
            sessions = listOf(
                TrainingSessionEntity(
                    id = "today",
                    plannedEpochDay = today,
                    title = "Проверочная тренировка",
                    type = "STRENGTH",
                    phase = "BASE",
                    objective = "test",
                    durationMinutes = 60,
                    targetRpe = 5,
                    stepsJson = "[]",
                ),
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                CalendarScreen(
                    padding = PaddingValues(),
                    state = state,
                    onOpenSession = {},
                    onReschedule = { _, _, _ -> },
                    onProposeNextBlock = {},
                )
            }
        }

        composeRule.onNodeWithText("Календарь").assertIsDisplayed()
        composeRule.onNodeWithText("Проверочная тренировка").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 запланировано").performScrollTo().assertIsDisplayed()
    }
}
