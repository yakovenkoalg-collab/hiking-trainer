package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType

class SeasonGoalsTest {
    private fun spring(notes: String) = GoalEventEntity(
        id = SeasonGoals.SPRING_RACE_ID, type = GoalType.RUNNING, title = "Весенний старт: 21,1 км",
        targetEpochDay = null, distanceKm = 21.1, priority = 8, status = "HALF_MARATHON_BASE", notes = notes,
    )

    @Test
    fun seasonEntriesAreAddedAndTheOldDefaultNoteIsRefreshed() {
        val upserts = SeasonGoals.pendingUpserts(
            listOf(spring("Предварительно полумарафон; марафон решается после беговой базы")),
        )
        val refreshed = upserts.single { it.id == SeasonGoals.SPRING_RACE_ID }
        assertTrue(refreshed.notes.startsWith("Цель 1:45"))
        assertEquals("HALF_MARATHON_BASE", refreshed.status)
        assertEquals(42.2, upserts.single { it.id == SeasonGoals.AUTUMN_MARATHON_ID }.distanceKm)
        val checkpoints = upserts.filter { it.type == GoalType.CHECKPOINT }
        assertEquals(3, checkpoints.size)
        assertTrue(checkpoints.all { it.targetEpochDay != null })
        assertEquals(checkpoints.map { it.targetEpochDay }, checkpoints.map { it.targetEpochDay }.sortedBy { it })
    }

    @Test
    fun userEditsAndExistingEntriesAreNeverOverwritten() {
        val edited = spring("Моя заметка")
        val first = SeasonGoals.pendingUpserts(listOf(edited))
        assertTrue(first.none { it.id == SeasonGoals.SPRING_RACE_ID })
        val editedMarathon = first.single { it.id == SeasonGoals.AUTUMN_MARATHON_ID }.copy(notes = "Перенёс на ноябрь")
        val stored = listOf(edited, editedMarathon) + first.filter { it.type == GoalType.CHECKPOINT }
        assertTrue(SeasonGoals.pendingUpserts(stored).isEmpty())
    }
}
