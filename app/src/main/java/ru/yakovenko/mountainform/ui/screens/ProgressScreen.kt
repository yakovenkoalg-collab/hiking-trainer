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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onSaveBodyMetric: (Double?, Double?, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
) {
    var showBodyMetric by remember { mutableStateOf(false) }
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
                MetricCard("VO₂max, исходный", "42", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Garmin за ${healthSummary.windowDays} дней") }
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
            Button(onClick = { showBodyMetric = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Записать вес, талию и питание")
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

    if (showBodyMetric) {
        BodyMetricDialog(
            initialWeight = latestBody?.weightKg ?: state.profile?.weightKg,
            initialWaist = latestBody?.waistCm,
            onDismiss = { showBodyMetric = false },
            onSave = { weight, waist, protein, produce, hydration, alcoholFree, notes ->
                onSaveBodyMetric(weight, waist, protein, produce, hydration, alcoholFree, notes)
                showBodyMetric = false
            },
        )
    }
}

@Composable
private fun BodyMetricDialog(
    initialWeight: Double?,
    initialWaist: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
) {
    var weight by remember { mutableStateOf(initialWeight?.toString().orEmpty()) }
    var waist by remember { mutableStateOf(initialWaist?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(false) }
    var produce by remember { mutableStateOf(false) }
    var hydration by remember { mutableStateOf(false) }
    var alcoholFree by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Метрики сегодня") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.replace(',', '.') },
                    label = { Text("Вес, кг") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = waist,
                    onValueChange = { waist = it.replace(',', '.') },
                    label = { Text("Талия, см") },
                    modifier = Modifier.fillMaxWidth(),
                )
                MetricCheck("Белок по плану", protein) { protein = it }
                MetricCheck("Овощи и фрукты", produce) { produce = it }
                MetricCheck("Достаточно воды", hydration) { hydration = it }
                MetricCheck("Без алкоголя", alcoholFree) { alcoholFree = it }
                OutlinedTextField(notes, { notes = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(weight.toDoubleOrNull(), waist.toDoubleOrNull(), protein, produce, hydration, alcoholFree, notes)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun MetricCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
