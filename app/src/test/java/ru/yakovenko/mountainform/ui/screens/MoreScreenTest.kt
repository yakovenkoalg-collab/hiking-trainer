package ru.yakovenko.mountainform.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MoreScreenTest {
    @Test
    fun ageIsDeclinedInRussian() {
        assertEquals(
            listOf("21 год", "32 года", "41 год", "45 лет", "50 лет", "111 лет", "112 лет"),
            listOf(21, 32, 41, 45, 50, 111, 112).map(::ageLabel),
        )
    }
}
