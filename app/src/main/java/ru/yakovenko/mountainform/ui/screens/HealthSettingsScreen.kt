package ru.yakovenko.mountainform.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.components.MetricCard
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
    onImportFit: (android.net.Uri) -> Unit,
) {
    var showHowItWorks by remember { mutableStateOf(false) }
    var showFitHelp by remember { mutableStateOf(false) }
    val metrics = buildList {
        if (summary.workouts > 0) add("тренировки" to summary.workouts.toString())
        if (summary.steps > 0) add("шаги" to summary.steps.toString())
        if (summary.distanceKm > 0) add("дистанция" to "${summary.distanceKm.oneDecimal()} км")
        if (summary.elevationMeters > 0) add("набор" to "${summary.elevationMeters.toInt()} м")
        summary.latestSleepHours?.let { add("последний сон" to "${it.oneDecimal()} ч") }
        summary.averageHeartRate?.let { add("средний пульс" to "${it.toInt()} уд/мин") }
    }
    val fitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportFit(uri)
    }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        "Проверено ${formatInstant(it)}",
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = summary.windowDays == days,
                            onClick = { onWindowChange(days) },
                            label = { Text("$days дн.") },
                        )
                    }
                }
            }
            items(metrics.chunked(2).size) { rowIndex ->
                val row = metrics.chunked(2)[rowIndex]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) -> MetricCard(label, value, Modifier.weight(1f)) }
                    if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
            if (metrics.isEmpty() && summary.permissionsGranted) {
                item {
                    Card { Text("Данных за выбранный период нет", Modifier.padding(16.dp)) }
                }
            }
            item {
                TextButton(onClick = { showHowItWorks = !showHowItWorks }) {
                    Text(if (showHowItWorks) "Скрыть источники" else "Источники и безопасность")
                }
            }
            if (showHowItWorks) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Источники", fontWeight = FontWeight.Bold)
                            if (summary.dataOrigins.isEmpty()) {
                                Text("Источник пока не определён", style = MaterialTheme.typography.bodySmall)
                            } else {
                                summary.dataOrigins.forEach { Text("• ${friendlyOrigin(it)}", style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(
                                "Garmin Connect передаёт данные через Health Connect. Данные читаются локально, пароль Garmin приложению не передаётся.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Импорт Garmin FIT", fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { fitPicker.launch(arrayOf("application/octet-stream", "application/vnd.ant.fit", "application/zip", "*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Выбрать FIT или ZIP") }
                        TextButton(onClick = { showFitHelp = !showFitHelp }) {
                            Text(if (showFitHelp) "Скрыть пояснение" else "Когда это нужно")
                        }
                        if (showFitHelp) {
                            Text(
                                "Импорт добавляет Training Effect, нагрузку, круги, зоны и беговую динамику, если их нет в Health Connect. Повторный импорт не создаёт дубликат.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
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
