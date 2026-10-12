package ru.yakovenko.mountainform.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.domain.HeartRateTarget
import ru.yakovenko.mountainform.domain.RunningWeek

class SeasonProgressTest {
    private fun laps(vararg heartRates: Double) =
        heartRates.mapIndexed { index, rate -> ActivityLapSummary(index + 1, 360, averageHeartRate = rate) }

    @Test
    fun complianceTextNamesTheCeilingTheAverageAndTheEstimate() {
        val above = heartRateComplianceText(
            requireNotNull(
                HeartRateTarget.compliance(
                    148, 163.4, 2_880, laps(147.0, 159.0, 161.0, 160.0, 166.0, 163.0, 165.0, 169.0),
                    zoneHighBoundaries = listOf(96.0, 115.0, 134.0, 154.0, 173.0, 192.0),
                    zoneSeconds = listOf(0.0, 8.0, 44.0, 500.0, 2_300.0, 28.0, 0.0),
                ),
            ),
        )
        assertEquals("Пульс выше границы 148 · средний 163", above.headline)
        assertEquals("Выше границы 7 из 8 кругов · около 42 из 48 мин · по зонам часов не меньше 39 мин", above.detail)
        assertTrue(above.needsAttention)

        val within = heartRateComplianceText(requireNotNull(HeartRateTarget.compliance(148, 144.0, 1_080, laps(142.0, 145.0, 146.0))))
        assertEquals("Пульс в границе 148 · средний 144", within.headline)
        assertEquals("Выше границы 0 из 3 кругов · около 0 из 18 мин", within.detail)
        assertFalse(within.needsAttention)

        val averageOnly = heartRateComplianceText(requireNotNull(HeartRateTarget.compliance(148, 151.0, 1_800, emptyList())))
        assertEquals("Оценка по среднему пульсу: кругов в записи нет", averageOnly.detail)
    }

    @Test
    fun easyShareCoversOnlyTheLatestFourWeeksWithHeartRate() {
        fun week(assessed: Long, easy: Long) = RunningWeek(0, 1, 5_000.0, 1_800, assessed, easy)
        assertNull(recentEasySharePercent(listOf(week(0, 0), week(0, 0))))
        val weeks = listOf(week(1_000, 1_000), week(1_000, 0), week(0, 0), week(2_000, 1_500), week(1_000, 0))
        assertEquals(38, recentEasySharePercent(weeks))
    }

    @Test
    fun easyPaceTrendComparesTheLatestFourWeeksWithTheFourBefore() {
        fun week(seconds: Long, meters: Double) = RunningWeek(0, 1, meters, seconds, seconds, seconds, seconds, meters)
        val earlier = List(4) { week(4_250, 10_000.0) } // 7:05/км
        val recent = List(4) { week(4_120, 10_000.0) }  // 6:52/км
        assertEquals("Темп на пульсе до 148: 6:52/км · месяцем раньше 7:05/км", easyPaceTrendText(earlier + recent, 148))
        assertEquals("Темп на пульсе до 148: 6:52/км", easyPaceTrendText(List(4) { week(0, 0.0) } + recent, 148))
        assertNull(easyPaceTrendText(List(8) { week(300, 700.0 / 4) }, 148))
    }

    @Test
    fun countsAndDistancesInDaysAreDeclinedInRussian() {
        assertEquals(listOf("1 пробежка", "3 пробежки", "5 пробежек", "11 пробежек", "22 пробежки"),
            listOf(1, 3, 5, 11, 22).map(::runCount))
        assertEquals(listOf("сегодня", "завтра", "через 2 дня", "через 5 дней", "через 21 день", "через 62 дня", "через 112 дней"),
            listOf(0L, 1L, 2L, 5L, 21L, 62L, 112L).map(::daysUntil))
    }

    @Test
    fun onlyTheNextThreeCheckpointsAreShown() {
        fun checkpoint(day: Long?) = GoalEventEntity("c$day", GoalType.CHECKPOINT, "Контроль", day, null, 5, "PLANNED", "")
        val goals = listOf(checkpoint(40), checkpoint(10), checkpoint(null), checkpoint(20), checkpoint(30), checkpoint(50)) +
            GoalEventEntity("race", GoalType.RUNNING, "Старт", 25, 21.1, 8, "PLANNED", "")
        assertEquals(listOf(20L, 30L, 40L), upcomingCheckpoints(goals, todayEpochDay = 20).map { it.targetEpochDay })
    }
}
