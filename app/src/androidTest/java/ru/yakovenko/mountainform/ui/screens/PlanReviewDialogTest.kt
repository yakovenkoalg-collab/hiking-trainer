package ru.yakovenko.mountainform.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import ru.yakovenko.mountainform.data.*
import ru.yakovenko.mountainform.domain.HomeRunningBlock
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import java.time.LocalDate

class PlanReviewDialogTest {
    @get:Rule val rule = createComposeRule()

    private fun preview(conflicts: List<String> = emptyList()): ImportPreview {
        val plan = HomeRunningBlock.envelope(LocalDate.of(2026, 9, 10))
        return ImportPreview(plan, plan.sessions.size, 0, 0, 0, conflicts,
            plan.sessions.map { s -> PlanSessionChange(null, PlanSessionSummary(s.plannedEpochDay, s.title,
                s.durationMinutes, s.targetRpe, s.steps.map { it.title + " — " + it.prescription })) }, emptyList(), emptyList())
    }

    @Test fun compactPreviewKeepsApplyVisibleAndExpandsExercises() {
        var applied = false
        rule.setContent { MountainFormTheme { PlanReviewDialog(preview(), {}, { applied = true }) } }
        rule.onNodeWithText("Изменения плана").assertIsDisplayed()
        rule.onNodeWithText("Применить").assertIsDisplayed().assertIsEnabled()
        rule.onAllNodesWithText("Упражнения (3)")[0].performClick()
        rule.onNodeWithText("Ходьба и разминка — 5 мин · RPE 2–3").assertIsDisplayed()
        rule.onNodeWithText("Применить").performClick()
        assertTrue(applied)
    }

    @Test fun conflictsBlockApplyButAllowClosing() {
        var dismissed = false
        rule.setContent { MountainFormTheme { PlanReviewDialog(preview(listOf("Проверьте плечо")), { dismissed = true }, {}) } }
        rule.onNodeWithText("Применить").assertIsNotEnabled()
        rule.onNodeWithText("Отмена").performClick()
        assertTrue(dismissed)
    }
}
