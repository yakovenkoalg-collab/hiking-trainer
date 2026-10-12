package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType
import java.time.LocalDate

/** Agreed goals and control points of the 2026–27 season. Added once; later edits by the user are kept. */
object SeasonGoals {
    const val SPRING_RACE_ID = "spring-road-race-2027"
    const val AUTUMN_MARATHON_ID = "autumn-marathon-2027"

    private const val SUPERSEDED_SPRING_NOTES = "Предварительно полумарафон; марафон решается после беговой базы"
    private const val SPRING_NOTES = "Цель 1:45 · темп 4:58/км · конец апреля – середина мая"

    private val additions = listOf(
        GoalEventEntity(
            id = AUTUMN_MARATHON_ID,
            type = GoalType.RUNNING,
            title = "Осенний марафон: 42,2 км",
            targetEpochDay = null,
            distanceKm = 42.2,
            priority = 7,
            status = "PLANNED",
            notes = "Цель быстрее 4:00 · темп 5:38–5:41/км · не раньше чем через 11–12 недель после похода",
        ),
        checkpoint("checkpoint-2026-12-13", LocalDate.of(2026, 12, 13), "Контрольные 5 км на лёгком пульсе",
            "5 км на пульсе 145–148 по одному маршруту: темп быстрее, чем в октябре"),
        checkpoint("checkpoint-2027-02-07", LocalDate.of(2027, 2, 7), "Контрольные 10 км",
            "В полную силу, не медленнее 50:00"),
        checkpoint("checkpoint-2027-03-28", LocalDate.of(2027, 3, 28), "Контрольные 10 км",
            "47:30 — цель 1:45 реальна; 48–49 минут — цель 1:47–1:49"),
    )

    /** Goals to write: missing season entries plus the spring race note, only while it still holds the old default. */
    fun pendingUpserts(existing: List<GoalEventEntity>): List<GoalEventEntity> {
        val existingIds = existing.mapTo(mutableSetOf()) { it.id }
        val refreshedSpring = existing
            .firstOrNull { it.id == SPRING_RACE_ID && it.notes == SUPERSEDED_SPRING_NOTES }
            ?.copy(notes = SPRING_NOTES)
        return listOfNotNull(refreshedSpring) + additions.filter { it.id !in existingIds }
    }

    private fun checkpoint(id: String, date: LocalDate, title: String, notes: String) = GoalEventEntity(
        id = id,
        type = GoalType.CHECKPOINT,
        title = title,
        targetEpochDay = date.toEpochDay(),
        distanceKm = null,
        priority = 5,
        status = "PLANNED",
        notes = notes,
    )
}
