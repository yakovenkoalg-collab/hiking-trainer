package ru.yakovenko.mountainform.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("d MMM, EEE", Locale.forLanguageTag("ru"))

fun formatEpochDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDate)

fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
