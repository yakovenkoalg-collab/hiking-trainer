package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.MetricCard
import ru.yakovenko.mountainform.ui.oneDecimal
import java.time.LocalDate

@Composable
fun ProgressScreen(
    padding: PaddingValues,
    state: AppUiState,
    healthSummary: HealthSummary,
    onSaveBodyMetric: (Double?, Double?, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
    onOpenActivities: () -> Unit,
) {
    var showBodyMetric by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val weekStart = today - 6
    val weekSessions = state.sessions.filter { it.plannedEpochDay in weekStart..today }
    val completed = weekSessions.count { it.status == SessionStatus.COMPLETED }
    val skipped = weekSessions.count { it.status == SessionStatus.SKIPPED }
    val planned = weekSessions.size
    val completion = if (planned == 0) 0f else completed.toFloat() / planned
    val practices = state.practices.count { it.epochDay in weekStart..today }
    val latestBody = state.bodyMetrics.firstOrNull()
    val healthMetrics = buildList {
        if (healthSummary.workouts > 0) add("тренировок" to healthSummary.workouts.toString())
        if (healthSummary.distanceKm > 0) add("дистанция" to "${healthSummary.distanceKm.oneDecimal()} км")
        if (healthSummary.elevationMeters > 0) add("набор" to "${healthSummary.elevationMeters.toInt()} м")
        if (healthSummary.steps > 0) add("шаги" to healthSummary.steps.toString())
        healthSummary.latestSleepHours?.let { add("последний сон" to "${it.oneDecimal()} ч") }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Прогресс", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (planned == 0) {
                        Text("Последние 7 дней", fontWeight = FontWeight.Bold)
                        Text("В этом периоде тренировок нет", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Последние 7 дней", fontWeight = FontWeight.Bold)
                            Text("$completed из $planned")
                        }
                        LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth())
                        if (skipped > 0) {
                            Text("Пропущено: $skipped", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            val garmin = garminActivitySummary(state.importedActivities, healthSummary)
            Card(onClick = onOpenActivities) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Тренировки Garmin", fontWeight = FontWeight.Bold)
                    Text(
                        garmin.text,
                        color = if (garmin.needsAttention) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            if (practices > 0) {
                MetricCard("core и осанка за 7 дней", "$practices × 10 мин", Modifier.fillMaxWidth())
            } else {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Core и осанка", fontWeight = FontWeight.Bold)
                        Text("За 7 дней пока нет отметок", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(healthMetrics.chunked(2).size) { rowIndex ->
            val row = healthMetrics.chunked(2)[rowIndex]
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    MetricCard(label, value, Modifier.weight(1f))
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        if (latestBody != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("вес", latestBody.weightKg?.let { "${it.oneDecimal()} кг" } ?: "—", Modifier.weight(1f))
                    MetricCard("талия", latestBody.waistCm?.let { "${it.oneDecimal()} см" } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item {
            TextButton(onClick = { showBodyMetric = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (latestBody == null) "Добавить показатели тела" else "Обновить показатели тела")
            }
        }
        state.goals.firstOrNull { it.type == GoalType.RUNNING }?.let { goal ->
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Беговая цель", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(goal.notes, style = MaterialTheme.typography.bodySmall)
                    }
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
    val parsedWeight = weight.toDoubleOrNull()
    val parsedWaist = waist.toDoubleOrNull()
    val weightInvalid = weight.isNotBlank() && (parsedWeight == null || parsedWeight < 35.0 || parsedWeight > 250.0)
    val waistInvalid = waist.isNotBlank() && (parsedWaist == null || parsedWaist < 40.0 || parsedWaist > 200.0)
    val formValid = !weightInvalid && !waistInvalid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Метрики сегодня") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.replace(',', '.').filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Вес, кг") },
                    isError = weightInvalid,
                    supportingText = { if (weightInvalid) Text("Допустимо 35–250 кг") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                ) }
                item { OutlinedTextField(
                    value = waist,
                    onValueChange = { waist = it.replace(',', '.').filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Талия, см") },
                    isError = waistInvalid,
                    supportingText = { if (waistInvalid) Text("Допустимо 40–200 см") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                ) }
                item { MetricCheck("Белок по плану", protein) { protein = it } }
                item { MetricCheck("Овощи и фрукты", produce) { produce = it } }
                item { MetricCheck("Достаточно воды", hydration) { hydration = it } }
                item { MetricCheck("Без алкоголя", alcoholFree) { alcoholFree = it } }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                enabled = formValid,
                onClick = { onSave(parsedWeight, parsedWaist, protein, produce, hydration, alcoholFree, notes) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun MetricCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
