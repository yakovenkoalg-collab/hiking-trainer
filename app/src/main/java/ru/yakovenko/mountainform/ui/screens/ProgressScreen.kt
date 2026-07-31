package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.MetricCard
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.oneDecimal
import java.time.LocalDate

@Composable
fun ProgressScreen(
    padding: PaddingValues,
    state: AppUiState,
    healthSummary: HealthSummary,
) {
    val today = LocalDate.now().toEpochDay()
    val weekStart = today - 6
    val weekSessions = state.sessions.filter { it.plannedEpochDay in weekStart..today }
    val completed = weekSessions.count { it.status == SessionStatus.COMPLETED }
    val planned = weekSessions.size
    val completion = if (planned == 0) 0f else completed.toFloat() / planned
    val practices = state.practices.count { it.epochDay in weekStart..today }
    val latestBody = state.bodyMetrics.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("Прогресс", "Одна нагрузка для бега, гор и силовой работы") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Выполнение недели", fontWeight = FontWeight.Bold)
                        Text("$completed из $planned")
                    }
                    LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (completion >= 0.8f) "Стабильность важнее добавления объёма" else "Сначала закрепляем регулярность",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("core / осанка за 7 дней", "$practices × 10 мин", Modifier.weight(1f))
                MetricCard("VO₂max Garmin", "42", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Garmin за 7 дней") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("тренировок", healthSummary.workouts.toString(), Modifier.weight(1f))
                MetricCard("дистанция", "${healthSummary.distanceKm.oneDecimal()} км", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("набор", "${healthSummary.elevationMeters.toInt()} м", Modifier.weight(1f))
                MetricCard("шаги", healthSummary.steps.toString(), Modifier.weight(1f))
            }
        }
        item { SectionTitle("Тело и питание") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("вес", latestBody?.weightKg?.let { "${it.oneDecimal()} кг" } ?: "—", Modifier.weight(1f))
                MetricCard("талия", latestBody?.waistCm?.let { "${it.oneDecimal()} см" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Беговая контрольная точка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("После 8–12 недель регулярного бега сравним длинную пробежку, недельный объём и восстановление.")
                    Text("Решение: оставить 21,1 км или переходить к 42,2 км", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
