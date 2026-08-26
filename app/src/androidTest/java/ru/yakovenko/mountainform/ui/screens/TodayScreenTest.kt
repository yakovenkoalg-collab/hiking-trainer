package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.data.ReadinessCheckEntity
import ru.yakovenko.mountainform.data.ReviewCheckpointEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.UserProfileEntity
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.LocalDate

class TodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowScreenWithLargeFontKeepsReadinessAndPrimaryActionReadable() {
        var openedSession: String? = null
        val today = LocalDate.now().toEpochDay()
        val state = testState(today)

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                MountainFormTheme {
                    Box(Modifier.width(320.dp).height(720.dp)) {
                        TodayScreen(
                            padding = PaddingValues(),
                            state = state,
                            onSaveReadiness = { _, _, _, _, _, _, _, _ -> },
                            onOpenSession = { openedSession = it },
                            onCompleteCorePractice = {},
                            onShareReviewReport = {},
                        )
                    }
                }
            }
        }

        val readinessWidth = composeRule.onNodeWithTag("readiness_title")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertTrue("Readiness title was squeezed to $readinessWidth px", readinessWidth >= 120f)

        composeRule.onNodeWithTag("open_next_session")
            .assertIsDisplayed()
            .performClick()
        assertTrue(openedSession == "next")
        assertTrue(composeRule.onAllNodesWithText("Весенняя цель").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun completedPracticeAndPlanReviewUseClearStatuses() {
        val today = LocalDate.now().toEpochDay()
        val state = testState(today).copy(
            practices = listOf(
                ru.yakovenko.mountainform.data.PracticeLogEntity(
                    id = "core-$today",
                    epochDay = today,
                    type = "CORE_POSTURE",
                    minutes = 10,
                    notes = "",
                ),
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                TodayScreen(
                    padding = PaddingValues(),
                    state = state,
                    onSaveReadiness = { _, _, _, _, _, _, _, _ -> },
                    onOpenSession = {},
                    onCompleteCorePractice = {},
                    onShareReviewReport = {},
                )
            }
        }

        composeRule.onNodeWithText("Выполнено").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Сформировать отчёт").performScrollTo().assertIsDisplayed()
    }

    private fun testState(today: Long) = AppUiState(
        profile = UserProfileEntity(
            age = 41,
            heightCm = 183,
            weightKg = 75.0,
            preferredDays = "Вторник, пятница, воскресенье",
            currentPhase = "Восстановление после похода",
            shoulderRestrictionActive = true,
            kneeObservationActive = true,
            updatedAtEpochMillis = 0,
        ),
        goals = listOf(
            GoalEventEntity(
                id = "spring",
                type = GoalType.RUNNING,
                title = "Весенняя цель",
                targetEpochDay = null,
                distanceKm = 21.1,
                priority = 8,
                status = "ACTIVE",
                notes = "Беговая база",
            ),
        ),
        sessions = listOf(
            TrainingSessionEntity(
                id = "next",
                plannedEpochDay = today + 2,
                title = "Ноги + core без нагрузки на плечо",
                type = "STRENGTH",
                phase = "BASE",
                objective = "Поддержать силу",
                durationMinutes = 65,
                targetRpe = 5,
                stepsJson = "[]",
            ),
        ),
        readiness = listOf(
            ReadinessCheckEntity(
                epochDay = today,
                sleep = 4,
                energy = 4,
                fatigue = 2,
                soreness = 1,
                shoulderPain = 0,
                kneePain = 0,
                illness = false,
                notes = "",
                createdAtEpochMillis = 0,
            ),
        ),
        reviewCheckpoints = listOf(
            ReviewCheckpointEntity(
                id = "review",
                createdAtEpochMillis = 0,
                completedSessionIdsJson = "[]",
                reason = "Завершены три ключевые тренировки",
            ),
        ),
    )
}
