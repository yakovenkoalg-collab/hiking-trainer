package ru.yakovenko.mountainform.ui

import org.junit.Assert.*
import org.junit.Test
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import java.time.LocalDate

class NextSessionTest {
    private val today = LocalDate.now().toEpochDay()
    private fun session(id: String, day: Long, status: String = "PLANNED") =
        TrainingSessionEntity(id, day, "Test", "RUN", "BASE", "Test", 30, 3, "[]", status)

    @Test fun neverOffersAnOldUncompletedWorkout() {
        val old = session("old", today - 52)
        val state = AppUiState(sessions = listOf(old, session("done", today, "COMPLETED")))
        assertNull(state.nextSession)
        assertEquals("PLANNED", state.sessions.first().status)
    }

    @Test fun selectsNearestDateRegardlessOfInputOrder() {
        val state = AppUiState(sessions = listOf(session("later", today + 6),
            session("old", today - 1), session("next", today + 1)))
        assertEquals("next", state.nextSession?.id)
    }

    @Test fun todayTakesPriorityAndCompletedOrSkippedAreExcluded() {
        val state = AppUiState(sessions = listOf(session("done", today, "COMPLETED"),
            session("skipped", today, "SKIPPED"), session("future", today + 1), session("today", today)))
        assertEquals("today", state.nextSession?.id)
        assertNull(AppUiState().nextSession)
    }
}
