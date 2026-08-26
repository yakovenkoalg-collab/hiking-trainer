package ru.yakovenko.mountainform.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarHelpersTest {
    @Test
    fun monthChangeKeepsDayAndClampsAtMonthEnd() {
        assertEquals(LocalDate.of(2026, 2, 28), dateInMonth(LocalDate.of(2026, 1, 31), YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2026, 9, 15), dateInMonth(LocalDate.of(2026, 8, 15), YearMonth.of(2026, 9)))
    }

    @Test
    fun russianStatusPluralUsesCorrectForms() {
        assertEquals("1 выполнена", statusCount(1, "выполнена", "выполнены", "выполнено"))
        assertEquals("2 выполнены", statusCount(2, "выполнена", "выполнены", "выполнено"))
        assertEquals("11 выполнено", statusCount(11, "выполнена", "выполнены", "выполнено"))
    }
}
