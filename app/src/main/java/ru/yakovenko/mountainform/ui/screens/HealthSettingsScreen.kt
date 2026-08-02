package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.components.MetricCard
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.oneDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HealthSettingsScreen(
    summary: HealthSummary,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onWindowChange: (Int) -> Unit,
    onShowPrivacy: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garmin и Health Connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    when {
                                        summary.permissionsGranted && summary.hasAnyData -> "Подключено, данные получены"
                                        summary.permissionsGranted -> "Чтение разрешено, записей нет"
                                        summary.available -> "Нужно разрешение на чтение"
                                        else -> "Health Connect недоступен"
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                                summary.refreshedAtEpochMillis?.let {
                                    Text(
                                        "Проверено ${formatInstant(it)} · период ${summary.windowDays} дней",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        summary.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (summary.permissionsGranted) {
                            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Обновить данные") }
                        } else {
                            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) { Text("Разрешить чтение") }
                        }
                    }
                }
            }
            item { SectionTitle("Получено за ${summary.windowDays} дней") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = summary.windowDays == days,
                            onClick = { onWindowChange(days) },
                            label = { Text("$days дней") },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("тренировки", summary.workouts.toString(), Modifier.weight(1f))
                    MetricCard("шаги", summary.steps.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("дистанция", "${summary.distanceKm.oneDecimal()} км", Modifier.weight(1f))
                    MetricCard("набор", "${summary.elevationMeters.toInt()} м", Modifier.weight(1f))
                }
            }
            summary.latestSleepHours?.let { sleep ->
                item { Text("Последний сон: ${sleep.oneDecimal()} ч") }
            }
            summary.averageHeartRate?.let { pulse ->
                item { Text("Средний пульс доступных записей: ${pulse.toInt()} уд/мин") }
            }
            item { SectionTitle("Источники данных") }
            if (summary.dataOrigins.isEmpty()) {
                item {
                    Text(
                        "Источник пока не определён. Убедитесь, что Garmin Connect имеет разрешение на запись, затем синхронизируйте часы.",
                    )
                }
            } else {
                items(summary.dataOrigins.size) { index ->
                    Text("• ${friendlyOrigin(summary.dataOrigins.elementAt(index))}")
                }
            }
            item {
                Text(
                    "Garmin Connect передаёт данные в Health Connect, а Горная форма читает их локально. " +
                        "Приложение не получает пароль Garmin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { TextButton(onClick = onShowPrivacy) { Text("Как используются данные") } }
        }
    }
}

private fun friendlyOrigin(packageName: String): String = when (packageName) {
    "com.garmin.android.apps.connectmobile" -> "Garmin Connect"
    "android" -> "Этот телефон"
    else -> packageName
}

private fun formatInstant(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
