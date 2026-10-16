package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.domain.BaseOneBlock
import ru.yakovenko.mountainform.domain.HeartRateTarget
import ru.yakovenko.mountainform.domain.RunningVolume
import ru.yakovenko.mountainform.domain.SeasonGoals
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class SeasonProgressUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.now()
    private val monday: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun run(id: String, date: LocalDate, heartRates: List<Double>, lapSeconds: Long, linked: String? = null): ImportedActivityEntity {
        val start = date.atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val laps = heartRates.mapIndexed { index, rate ->
            ActivityLapSummary(index + 1, lapSeconds, distanceMeters = 1_000.0, averageHeartRate = rate)
        }
        val seconds = lapSeconds * heartRates.size
        return ImportedActivityEntity(
            id = id, sourceRecordId = id, sourceType = ActivitySourceType.FIT, sourcePackage = "com.garmin.fit",
            title = "Бег", activityType = "Бег", startAtEpochMillis = start, endAtEpochMillis = start + seconds * 1000,
            durationSeconds = seconds, distanceMeters = heartRates.size * 1_000.0,
            averageHeartRate = heartRates.average(), maxHeartRate = heartRates.max() + 8,
            heartRateZoneBoundariesJson = "[96.0,115.0,134.0,154.0,173.0,192.0]",
            timeInHeartRateZonesJson = Json.encodeToString(
                listOf(0.0, 10.0, 40.0, seconds * 0.2, seconds * 0.75, seconds * 0.04, 0.0),
            ),
            lapsJson = Json.encodeToString(laps), linkedSessionId = linked,
            status = if (linked != null) ActivityLinkStatus.LINKED else ActivityLinkStatus.UNLINKED,
            importedAtEpochMillis = start,
        )
    }

    private fun easySession(date: LocalDate) = BaseOneBlock.sessions().first { it.type == "RUN" }.let { planned ->
        TrainingSessionEntity(
            id = "easy-${date.toEpochDay()}", plannedEpochDay = date.toEpochDay(), title = planned.title, type = planned.type,
            phase = planned.phase, objective = planned.objective, durationMinutes = planned.durationMinutes,
            targetRpe = planned.targetRpe, stepsJson = Json.encodeToString(planned.steps),
        )
    }

    @Test
    fun progressShowsWeeklyRunningEasyShareAndSeasonGoals() {
        val session = easySession(monday)
        val activities = listOf(
            run("hard-3", monday.minusWeeks(3).plusDays(1), listOf(158.0, 161.0, 163.0, 160.0, 166.0, 163.0, 165.0, 169.0), 358),
            run("hard-2", monday.minusWeeks(2).plusDays(2), listOf(150.0, 157.0, 158.0, 159.0, 160.0, 161.0, 162.0, 163.0), 372),
            run("mixed-1", monday.minusWeeks(1).plusDays(3), listOf(144.0, 147.0, 151.0, 155.0, 157.0), 395),
            run("easy-now", monday, listOf(141.0, 145.0, 146.0, 147.0, 148.0), 412, linked = session.id),
        )
        val weeks = RunningVolume.weeks(activities, listOf(session), today)
        val goals = SeasonGoals.pendingUpserts(emptyList()) + GoalEventEntity(
            SeasonGoals.SPRING_RACE_ID, GoalType.RUNNING, "Весенний старт: 21,1 км", null, 21.1, 8,
            "HALF_MARATHON_BASE", "Цель 1:45 · темп 4:58/км · конец апреля – середина мая",
        )
        composeRule.setContent {
            MountainFormTheme {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RunningWeeksCard(weeks, HeartRateTarget.currentTarget(listOf(session), today.toEpochDay()))
                    SeasonCard(goals, today.toEpochDay())
                }
            }
        }
        composeRule.onNodeWithText("Бег по неделям").assertIsDisplayed()
        composeRule.onNodeWithText("Эта неделя: 5,0 км · 1 пробежка").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Бег по неделям.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Лёгкого бега за 4 недели: 29% при ориентире 80%. Оценка по кругам Garmin.").assertIsDisplayed()
        composeRule.onNodeWithText("Темп на пульсе до 148: 6:47/км").assertIsDisplayed()
        composeRule.onNodeWithText("Цели сезона").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Осенний марафон: 42,2 км").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ближайшая контрольная точка").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Цель быстрее 4:00").assertIsDisplayed()
        composeRule.onNodeWithText("В полную силу, не медленнее 50:00").assertDoesNotExist()
        composeRule.onNodeWithTag("season_details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Контрольные точки").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("В полную силу, не медленнее 50:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sessionOverviewShowsFactsShortPointsAndOneRowPerStep() {
        val planned = BaseOneBlock.sessions().first { it.type == "STRENGTH" && it.durationMinutes == 60 }
        val session = TrainingSessionEntity(
            id = planned.id, plannedEpochDay = today.toEpochDay(), title = planned.title, type = planned.type,
            phase = planned.phase, objective = planned.objective, durationMinutes = planned.durationMinutes,
            targetRpe = planned.targetRpe, stepsJson = Json.encodeToString(planned.steps),
        )
        composeRule.setContent {
            MountainFormTheme {
                SessionScreen(
                    padding = androidx.compose.foundation.layout.PaddingValues(), session = session, steps = planned.steps,
                    catalog = emptyList(), stepLogs = emptyList(), setLogs = emptyList(), shoulderRestrictionActive = false,
                    loadBlocked = false, adaptationRequired = false, readinessRecommendation = "",
                    readinessReasons = emptyList(), initialExecutionState = null, onExecutionStateChanged = {},
                    onStepCompleted = { _, _, _ -> }, onSaveSetLog = {}, onBack = {}, onEditReadiness = {},
                    onComplete = { _, _, _, _ -> }, onSkip = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("60 мин").assertIsDisplayed()
        composeRule.onNodeWithText("RPE 6").assertIsDisplayed()
        composeRule.onNodeWithText("Не до отказа: 3–4 повтора в запасе.").assertIsDisplayed()
        composeRule.onNodeWithText("Без боли в колене и плече.").assertDoesNotExist()
        composeRule.onNodeWithTag("plan_points_toggle").performClick()
        composeRule.onNodeWithText("Без боли в колене и плече.").assertIsDisplayed()
        composeRule.onNodeWithText("3 × 10 · 16 кг", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Упражнений:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("plan_step_forearm-plank", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("plan_thumb_core", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithText("Закрыть схему").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("plan_thumb_push-up", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("plan_thumb_warmup", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun linkedRunShowsHowItComparesWithThePlannedCeiling() {
        val session = easySession(today)
        val activity = run("too-fast", today, listOf(147.0, 159.0, 161.0, 160.0, 166.0, 163.0, 165.0, 169.0), 360, linked = session.id)
        composeRule.setContent {
            MountainFormTheme {
                ActivitiesScreen(
                    activities = listOf(activity), sessions = listOf(session), healthSummary = HealthSummary(),
                    onBack = {}, onRequestPermissions = {}, onRefresh = {}, onWindowChange = {}, onShowPrivacy = {},
                    onImportFit = {}, onLinkActivity = { _, _ -> }, onIgnoreActivity = {}, onRestoreActivity = {},
                )
            }
        }
        composeRule.onNodeWithText("Пульс выше границы 148 · средний 161").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Выше границы 7 из 8 кругов", substring = true).assertIsDisplayed()
    }
}
