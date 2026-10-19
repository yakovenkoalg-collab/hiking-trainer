package ru.yakovenko.mountainform.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.yakovenko.mountainform.data.*
import ru.yakovenko.mountainform.domain.*
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.io.File
import java.time.LocalDate

class RecordingUxTest {
    @get:Rule val rule = createComposeRule()
    private val steps = listOf(ExerciseStep("forearm-plank", "Планка на предплечьях с длинным названием упражнения", "2 × 40 сек", "Без боли", blockTitle = "Длинное название блока: стабилизация и домашняя силовая работа"))

    @Test fun retrospectiveRequiresActualTimeAndDoesNotStartTimers() {
        var details: SessionCompletionDetails? = null
        var seconds = 0
        rule.setContent { MountainFormTheme {
            RetrospectiveSessionDialog("test", WorkoutPlanCompiler.compile(steps), emptyList(), {}, { _, _, duration, metadata ->
                details = metadata; seconds = duration
            })
        } }
        rule.onNodeWithTag("save_retrospective").assertIsNotEnabled()
        rule.onNodeWithTag("actual_minutes").performTextInput("30")
        rule.onNodeWithTag("retrospective_content").performScrollToNode(hasTestTag("retro_set_0"))
        rule.onNodeWithTag("retro_set_0").performClick()
        rule.onNodeWithTag("save_retrospective").performClick()
        rule.runOnIdle {
            assertEquals(1800, seconds)
            assertEquals("RETROSPECTIVE", details?.recordingMode)
            val log = requireNotNull(details).retrospectiveLogs.single()
            assertNull(log.actualRestSeconds); assertNull(log.startedAtEpochMillis); assertNull(log.actualReps)
        }
    }

    @Test fun retrospectiveMarksEverythingWithOneTap() {
        var logged = 0
        rule.setContent { MountainFormTheme {
            RetrospectiveSessionDialog("test", WorkoutPlanCompiler.compile(steps), emptyList(), {}, { _, _, _, metadata ->
                logged = metadata.retrospectiveLogs.size
            })
        } }
        rule.onNodeWithTag("actual_minutes").performTextInput("30")
        rule.onNodeWithTag("retrospective_content").performScrollToNode(hasTestTag("retro_select_all"))
        rule.onNodeWithTag("retro_select_all").performClick()
        rule.onNodeWithTag("save_retrospective").performClick()
        rule.runOnIdle { assertEquals(2, logged) }
    }

    @Test fun garminPrefillCountsTheWorkoutWithoutTyping() {
        var seconds = 0
        var details: SessionCompletionDetails? = null
        rule.setContent { MountainFormTheme {
            RetrospectiveSessionDialog("test", WorkoutPlanCompiler.compile(steps), emptyList(), {}, { _, _, duration, metadata ->
                seconds = duration; details = metadata
            }, prefill = GarminPrefill(LocalDate.now().minusDays(1).toEpochDay(), 48), initialRpe = 3)
        } }
        rule.onNodeWithText("Засчитать по Garmin").assertIsDisplayed()
        rule.onNodeWithTag("save_retrospective").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(48 * 60, seconds)
            assertEquals(LocalDate.now().minusDays(1).toEpochDay(), details?.performedEpochDay)
            assertEquals(2, details?.retrospectiveLogs?.size)
        }
    }

    @Test fun overviewAndExecutionSupportLargeFont() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MountainFormTheme { SessionScreen(
                    PaddingValues(), TrainingSessionEntity("test", LocalDate.now().toEpochDay(), "Дом", "STRENGTH", "BASE", "Описание", 30, 3, "[]"),
                    steps, emptyList(), emptyList(), emptyList(), false,
                    loadBlocked = false, adaptationRequired = false, readinessRecommendation = "", readinessReasons = emptyList(),
                    initialExecutionState = null, onExecutionStateChanged = {}, onStepCompleted = { _, _, _ -> },
                    onSaveSetLog = {}, onBack = {}, onEditReadiness = {}, onComplete = { _, _, _, _ -> }, onSkip = { _, _ -> },
                ) }
            }
        }
        rule.onNodeWithTag("start_workout_button").assertIsDisplayed()
        rule.onNodeWithTag("record_completed_workout").assertIsDisplayed()
        screenshot("overview-large-font")
        rule.onNodeWithTag("start_workout_button").performClick()
        rule.onNodeWithTag("set_timer_button").assertIsDisplayed()
        rule.onNodeWithText("40 сек").assertExists()
        screenshot("execution-large-font")
    }

    @Test fun imageCanOpenAndCloseAndEveryCurrentPlanExerciseHasArtwork() {
        HomeRunningBlock.sessions().flatMap { it.steps }.forEach { assertNotNull("Missing ${it.title}: ${it.imageKey()}", illustrationResource(it.imageKey())) }
        BaseOneBlock.sessions().flatMap { it.steps }
            .filter { it.catalogId() !in setOf("walk", "run-walk") }
            .forEach { assertNotNull("Missing ${it.title}: ${it.imageKey()}", stepIllustration(it)) }
        // Every catalog exercise except walking and running has a picture, including the ones for coming blocks.
        seedExerciseCatalog(kotlinx.serialization.json.Json)
            .filter { it.id !in setOf("walk", "aerobic", "run-walk") }
            .forEach { assertNotNull("Missing artwork: ${it.id}", illustrationResource(it.illustrationKey)) }
        rule.setContent { MountainFormTheme { ExerciseIllustration("forearm-plank") } }
        rule.onNodeWithTag("exercise_illustration").performClick()
        rule.onNodeWithText("Закрыть схему").assertIsDisplayed().performClick()
        rule.onNodeWithText("Увеличить схему").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Keep synthetic UI evidence after Gradle uninstalls the test application.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/mountain-qa-$name.png").close()
    }
}
