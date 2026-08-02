package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.SessionStatus
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

    @Test
    fun rescheduleUsesCompactRussianCalendar() {
        val today = LocalDate.now().toEpochDay()
        val state = AppUiState(
            sessions = listOf(
                TrainingSessionEntity(
                    id = "move",
                    plannedEpochDay = today,
                    title = "Переносимая тренировка",
                    type = "RUN",
                    phase = "BASE",
                    objective = "test",
                    durationMinutes = 45,
                    targetRpe = 4,
                    stepsJson = "[]",
                ),
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                CalendarScreen(PaddingValues(), state, {}, { _, _, _ -> }, {})
            }
        }

        composeRule.onNodeWithContentDescription("Перенести").performScrollTo().performClick()
        composeRule.onNodeWithText("Перенести тренировку").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("Пн").fetchSemanticsNodes().size >= 2)
        check(composeRule.onAllNodesWithText("Вс").fetchSemanticsNodes().size >= 2)
        composeRule.onNodeWithText("Новая дата:", substring = true).assertIsDisplayed()
    }

    @Test
    fun completedWorkoutIsExplicitlyMarkedInDaySummary() {
        val today = LocalDate.now().toEpochDay()
        val state = AppUiState(
            sessions = listOf(
                TrainingSessionEntity(
                    id = "completed",
                    plannedEpochDay = today,
                    title = "Выполненная тренировка",
                    type = "AEROBIC",
                    phase = "BASE",
                    objective = "test",
                    durationMinutes = 65,
                    targetRpe = 3,
                    stepsJson = "[]",
                    status = SessionStatus.COMPLETED,
                ),
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                CalendarScreen(PaddingValues(), state, {}, { _, _, _ -> }, {})
            }
        }

        composeRule.onNodeWithText("1 выполнено").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Выполнено").performScrollTo().assertIsDisplayed()
    }
}
