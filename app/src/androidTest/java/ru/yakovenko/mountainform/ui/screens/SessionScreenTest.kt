package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.data.SetTimingStatus
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.domain.WorkoutExecutionState
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.LocalDate

class SessionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun workoutOverviewStartsWorkoutBeforeExerciseTimer() {
        val session = TrainingSessionEntity(
            id = "timer-session",
            plannedEpochDay = LocalDate.now().toEpochDay(),
            title = "Проверка таймера",
            type = "STRENGTH",
            phase = "BASE",
            objective = "Проверить явный старт",
            durationMinutes = 60,
            targetRpe = 5,
            stepsJson = "[]",
        )
        val steps = listOf(
            ExerciseStep(
                id = "timed-step",
                title = "Тестовое упражнение",
                prescription = "60 секунд",
                instructions = "Без нагрузки",
                workSeconds = 60,
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = steps,
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = null,
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("60 мин · RPE 5").assertIsDisplayed()
        composeRule.onNodeWithText("План тренировки").assertIsDisplayed()
        composeRule.onNodeWithTag("start_workout_button").performClick()
        val sessionContent = composeRule.onNodeWithTag("session_content")
        sessionContent.performScrollToNode(hasTestTag("set_timer_button"))
        composeRule.onNodeWithTag("set_timer_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText("Приостановить этап", substring = true).assertIsDisplayed()
    }

    @Test
    fun blockedWorkoutShowsCauseAndOffersReadinessEdit() {
        var editRequested = false
        val session = TrainingSessionEntity(
            id = "blocked-session",
            plannedEpochDay = LocalDate.now().toEpochDay(),
            title = "Заблокированная тренировка",
            type = "RECOVERY",
            phase = "RECOVERY",
            objective = "Проверить объяснение",
            durationMinutes = 45,
            targetRpe = 3,
            stepsJson = "[]",
        )

        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = listOf(ExerciseStep("walk", "Ходьба", "10 минут", "Спокойно", workSeconds = 600)),
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = true,
                    loadBlocked = true,
                    adaptationRequired = false,
                    readinessRecommendation = "Не тренируйтесь до повторной оценки.",
                    readinessReasons = listOf("боль в левом плече 8/10"),
                    initialExecutionState = null,
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = { editRequested = true },
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        val sessionContent = composeRule.onNodeWithTag("session_content")
        sessionContent.performScrollToNode(hasTestTag("edit_readiness_button"))
        composeRule.onNodeWithText("Причина: боль в левом плече 8/10.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("edit_readiness_button").performClick()
        assert(editRequested)
    }

    @Test
    fun exerciseTimerCanBeResetToItsPlannedDuration() {
        val session = TrainingSessionEntity(
            id = "reset-session",
            plannedEpochDay = LocalDate.now().toEpochDay(),
            title = "Сброс таймера",
            type = "AEROBIC",
            phase = "RECOVERY",
            objective = "Проверить сброс",
            durationMinutes = 10,
            targetRpe = 3,
            stepsJson = "[]",
        )
        val steps = listOf(
            ExerciseStep(
                id = "timed-step",
                title = "Разминка",
                prescription = "60 секунд",
                instructions = "Спокойно",
                workSeconds = 60,
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = steps,
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = WorkoutExecutionState(
                        sessionId = session.id,
                        workoutStarted = true,
                        paused = false,
                        workoutElapsedSeconds = 30,
                        workRemainingSeconds = 25,
                        setElapsedSeconds = 35,
                    ),
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        val sessionContent = composeRule.onNodeWithTag("session_content")
        sessionContent.performScrollToNode(hasTestTag("reset_set_timer_button"))
        composeRule.onNodeWithTag("reset_set_timer_button").performClick()
        composeRule.onNodeWithText("Сбросить таймер этапа?").assertIsDisplayed()
        composeRule.onNodeWithText("Сбросить").performClick()

        composeRule.onNodeWithText("1:00").assertIsDisplayed()
    }

    @Test
    fun timedStageUsesDurationResultWithoutStrengthFields() {
        val session = testSession("timed-result", "Результат временного этапа")
        val steps = listOf(
            ExerciseStep(
                id = "walk",
                title = "Ходьба",
                prescription = "10 минут",
                instructions = "Спокойно",
                workSeconds = 600,
            ),
        )

        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = steps,
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = WorkoutExecutionState(
                        sessionId = session.id,
                        workoutStarted = true,
                        paused = false,
                        setElapsedSeconds = 120,
                        workRemainingSeconds = 480,
                    ),
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("session_content").performScrollToNode(hasTestTag("detailed_set_result_button"))
        composeRule.onNodeWithTag("detailed_set_result_button").performClick()
        composeRule.onNodeWithText("Результат этапа").assertIsDisplayed()
        composeRule.onNodeWithText("RPE этапа:", substring = true).assertIsDisplayed()
        check(composeRule.onAllNodesWithText("Повторения").fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodesWithText("Вес, кг (необязательно)").fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodesWithText("Повторов в запасе, RIR").fetchSemanticsNodes().isEmpty())
        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Сохранить").assertIsNotEnabled()
        composeRule.onNodeWithText("Опишите боль", substring = true).assertIsDisplayed()
    }

    @Test
    fun painActionPausesWorkoutBeforeShowingSafetyDialog() {
        val session = testSession("pain-pause", "Безопасная пауза")
        var savedState: WorkoutExecutionState? = null

        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = listOf(ExerciseStep("step", "Упражнение", "1 × 8", "Без боли", reps = 8)),
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = WorkoutExecutionState(
                        sessionId = session.id,
                        workoutStarted = true,
                        workoutTickStartedAtEpochMillis = System.currentTimeMillis(),
                        timerMode = ru.yakovenko.mountainform.domain.WorkoutTimerMode.SET,
                        timerTickStartedAtEpochMillis = System.currentTimeMillis(),
                    ),
                    onExecutionStateChanged = { savedState = it },
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("session_content").performScrollToNode(androidx.compose.ui.test.hasText("Возникла боль — остановиться"))
        composeRule.onNodeWithText("Возникла боль — остановиться").performClick()
        composeRule.onNodeWithText("Остаться на паузе").assertIsDisplayed()
        composeRule.runOnIdle { check(savedState?.paused == true) }
    }

    @Test
    fun skippingUnrecordedStageRequiresAReasonDialog() {
        val session = testSession("skip-stage", "Подтверждение пропуска")
        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = listOf(
                        ExerciseStep("first", "Первое", "1 × 8", "Спокойно", reps = 8),
                        ExerciseStep("second", "Второе", "1 × 8", "Спокойно", reps = 8),
                    ),
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = null,
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("start_workout_button").performClick()
        composeRule.onNodeWithTag("session_content").performScrollToNode(hasTestTag("next_stage_button"))
        composeRule.onNodeWithTag("next_stage_button").performClick()
        composeRule.onNodeWithText("Почему пропускаете этап?").assertIsDisplayed()
        composeRule.onNodeWithText("Пропустить этап").assertIsDisplayed()
    }

    @Test
    fun quickDoneRecordsUntimedSetWithoutOpeningDetailedForm() {
        val session = testSession("quick-done", "Быстрая фиксация")
        var savedLog: SessionSetLogEntity? = null
        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = listOf(ExerciseStep("core", "Core", "1 × 8", "Спокойно", reps = 8)),
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = null,
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = { savedLog = it },
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("start_workout_button").performClick()
        composeRule.onNodeWithTag("session_content").performScrollToNode(hasTestTag("complete_set_button"))
        composeRule.onNodeWithTag("complete_set_button").performClick()

        composeRule.onNodeWithText("Итог тренировки").assertIsDisplayed()
        check(composeRule.onAllNodesWithText("Результат подхода").fetchSemanticsNodes().isEmpty())
        composeRule.runOnIdle {
            check(savedLog?.timingStatus == SetTimingStatus.NOT_USED)
            check(savedLog?.elapsedSeconds == 0)
            check(savedLog?.actualReps == 8)
        }
    }

    @Test
    fun activeShoulderRestrictionBlocksConflictingPlanAtRuntime() {
        val session = testSession("shoulder-conflict", "Конфликт плана")
        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = listOf(ExerciseStep("push-up", "Отжимания", "3 × 8", "Без боли", reps = 8)),
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = true,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = null,
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("start_workout_button").assertIsNotEnabled()
        composeRule.onNodeWithText("несовместимое с активным ограничением", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun skippedWorkoutCanBeRestoredWithoutLosingRecordedWork() {
        val session = testSession("restore-skipped", "Вернуть в план").copy(
            status = ru.yakovenko.mountainform.data.SessionStatus.SKIPPED,
            completionNotes = "Не успел",
        )
        var restored: String? = null
        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = PaddingValues(),
                    session = session,
                    steps = listOf(ExerciseStep("walk", "Ходьба", "10 минут", "Спокойно", workSeconds = 600)),
                    catalog = emptyList(),
                    stepLogs = emptyList(),
                    setLogs = emptyList(),
                    shoulderRestrictionActive = false,
                    loadBlocked = false,
                    adaptationRequired = false,
                    readinessRecommendation = "",
                    readinessReasons = emptyList(),
                    initialExecutionState = null,
                    onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {},
                    onBack = {},
                    onEditReadiness = {},
                    onComplete = { _, _, _, _ -> },
                    onSkip = { _, _ -> },
                    onRestoreSkipped = { restored = it },
                )
            }
        }

        composeRule.onNodeWithText("Вернуть тренировку в план").performScrollTo().performClick()
        composeRule.onNodeWithText("Вернуть").performClick()
        composeRule.runOnIdle { check(restored == session.id) }
    }

    private fun testSession(id: String, title: String) = TrainingSessionEntity(
        id = id,
        plannedEpochDay = LocalDate.now().toEpochDay(),
        title = title,
        type = "STRENGTH",
        phase = "BASE",
        objective = "Проверка UX",
        durationMinutes = 60,
        targetRpe = 5,
        stepsJson = "[]",
    )
}
