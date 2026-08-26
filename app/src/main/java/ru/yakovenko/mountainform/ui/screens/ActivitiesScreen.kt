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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
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
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.formatEpochDay
import ru.yakovenko.mountainform.ui.oneDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ActivitiesScreen(
    activities: List<ImportedActivityEntity>,
    sessions: List<TrainingSessionEntity>,
    healthSummary: HealthSummary,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
    onWindowChange: (Int) -> Unit,
    onShowPrivacy: () -> Unit,
    onImportFit: (android.net.Uri) -> Unit,
    onLinkActivity: (String, String?) -> Unit,
    onIgnoreActivity: (String) -> Unit,
) {
    var linkingActivity by remember { mutableStateOf<ImportedActivityEntity?>(null) }
    var showSources by remember { mutableStateOf(false) }
    val fitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportFit(uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировки Garmin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(healthConnectionTitle(healthSummary), fontWeight = FontWeight.Bold)
                                Text(
                                    healthSummary.refreshedAtEpochMillis?.let { "Проверено ${formatActivityTime(it)}" }
                                        ?: "Сначала синхронизируйте часы с Garmin Connect",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        healthSummary.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (healthSummary.permissionsGranted) {
                            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Обновить данные") }
                        } else {
                            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) { Text("Разрешить чтение") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 30, 90).forEach { days ->
                                FilterChip(
                                    selected = healthSummary.windowDays == days,
                                    onClick = { onWindowChange(days) },
                                    label = { Text("$days дн.") },
                                )
                            }
                        }
                        healthSummaryLine(healthSummary)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    fitPicker.launch(
                                        arrayOf(
                                            "application/octet-stream",
                                            "application/vnd.ant.fit",
                                            "application/zip",
                                            "*/*",
                                        ),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Импорт FIT / ZIP") }
                            TextButton(onClick = { showSources = !showSources }) {
                                Text(if (showSources) "Скрыть" else "Источники")
                            }
                        }
                        if (showSources) {
                            Text(
                                if (healthSummary.dataOrigins.isEmpty()) {
                                    "Источник пока не определён"
                                } else {
                                    "Источники: ${healthSummary.dataOrigins.joinToString { friendlyOrigin(it) }}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Garmin Connect передаёт данные через Health Connect. FIT/ZIP добавляет Training Effect, нагрузку, круги, зоны и другие подробные показатели.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onShowPrivacy) { Text("Как используются данные") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Активности", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    activities.count { it.status == ActivityLinkStatus.UNLINKED }.takeIf { it > 0 }?.let {
                        Text("Не связано: $it", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (activities.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Нет тренировок Garmin", fontWeight = FontWeight.Bold)
                            Text(
                                if (healthSummary.permissionsGranted) {
                                    "Нажмите «Обновить данные» после синхронизации Garmin Connect или импортируйте FIT/ZIP."
                                } else {
                                    "Сначала разрешите чтение Health Connect. Если активности не передаются, импортируйте FIT/ZIP."
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                items(activities.size, key = { activities[it].id }) { index ->
                    val activity = activities[index]
                    ActivityCard(
                        activity = activity,
                        linkedSession = sessions.firstOrNull { it.id == activity.linkedSessionId },
                        onLink = { linkingActivity = activity },
                        onUnlink = { onLinkActivity(activity.id, null) },
                        onIgnore = { onIgnoreActivity(activity.id) },
                    )
                }
            }
        }
    }

    linkingActivity?.let { activity ->
        val activityDay = Instant.ofEpochMilli(activity.startAtEpochMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val candidates = sessions.sortedBy { abs(it.plannedEpochDay - activityDay) }.take(6)
        AlertDialog(
            onDismissRequest = { linkingActivity = null },
            title = { Text("Связать с тренировкой") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("${activity.title} · ${formatActivityTime(activity.startAtEpochMillis)}") }
                    items(candidates.size) { index ->
                        val session = candidates[index]
                        OutlinedButton(
                            onClick = { onLinkActivity(activity.id, session.id); linkingActivity = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${formatEpochDay(session.plannedEpochDay)} · ${session.title}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { linkingActivity = null }) { Text("Отмена") } },
        )
    }
}

private fun healthConnectionTitle(summary: HealthSummary): String = when {
    summary.permissionsGranted && summary.hasAnyData -> "Подключено, данные получены"
    summary.permissionsGranted -> "Чтение разрешено, записей нет"
    summary.available -> "Нужно разрешение на чтение"
    else -> "Health Connect недоступен"
}

private fun healthSummaryLine(summary: HealthSummary): String? = buildList {
    if (summary.workouts > 0) add("${summary.workouts} тренировок")
    if (summary.distanceKm > 0) add("${summary.distanceKm.oneDecimal()} км")
    if (summary.elevationMeters > 0) add("+${summary.elevationMeters.toInt()} м")
    summary.averageHeartRate?.let { add("пульс ${it.toInt()}") }
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun friendlyOrigin(packageName: String): String = when (packageName) {
    "com.garmin.android.apps.connectmobile" -> "Garmin Connect"
    "android" -> "Этот телефон"
    else -> packageName
}

@Composable
private fun ActivityCard(
    activity: ImportedActivityEntity,
    linkedSession: TrainingSessionEntity?,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onIgnore: () -> Unit,
) {
    var detailsVisible by remember(activity.id) { mutableStateOf(false) }
    val zoneSeconds = remember(activity.timeInHeartRateZonesJson) {
        decodeMetricList<Double>(activity.timeInHeartRateZonesJson)
    }
    val laps = remember(activity.lapsJson) { decodeMetricList<ActivityLapSummary>(activity.lapsJson) }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(activity.title, fontWeight = FontWeight.Bold)
            Text("${formatActivityTime(activity.startAtEpochMillis)} · ${formatActivityDuration(activity.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
            Text(
                buildString {
                    activity.distanceMeters?.let { append("${formatDistance(it)} км  ") }
                    formatPace(activity.durationSeconds, activity.distanceMeters)?.let { append("$it/км  ") }
                    activity.elevationMeters?.let { append("+${it.toInt()} м  ") }
                    activity.descentMeters?.let { append("−${it.toInt()} м") }
                }.ifBlank { activity.activityType },
                style = MaterialTheme.typography.bodySmall,
            )
            val secondaryMetrics = buildString {
                    activity.averageHeartRate?.let { append("пульс ${it.toInt()}") }
                    activity.maxHeartRate?.let { append(" / ${it.toInt()}  ") }
                    activity.averageCadence?.let { append("каденс ${it.toInt()} шаг/мин  ") }
                    activity.averagePowerWatts?.let { append("${it.toInt()} Вт") }
                }.trim()
            if (secondaryMetrics.isNotBlank()) Text(secondaryMetrics, style = MaterialTheme.typography.bodySmall)
            if (activity.aerobicTrainingEffect != null || activity.trainingLoad != null || laps.isNotEmpty()) {
                TextButton(onClick = { detailsVisible = !detailsVisible }) {
                    Text(if (detailsVisible) "Скрыть показатели" else "Показатели")
                }
            }
            if (detailsVisible) {
                ActivityDetails(activity, zoneSeconds, laps)
            }
            if (linkedSession != null) {
                Text("Связано: ${linkedSession.title}", color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onUnlink) { Text("Убрать связь") }
            } else if (activity.status != ActivityLinkStatus.IGNORED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLink, modifier = Modifier.weight(1f)) { Text("Связать") }
                    TextButton(onClick = onIgnore) { Text("Игнорировать") }
                }
            } else {
                Text("Игнорируется", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActivityDetails(
    activity: ImportedActivityEntity,
    zoneSeconds: List<Double>,
    laps: List<ActivityLapSummary>,
) {
    HorizontalDivider()
    activity.aerobicTrainingEffect?.let { aerobic ->
        Text(
            buildString {
                append("Training Effect: аэробный ${aerobic.oneDecimal()}")
                activity.anaerobicTrainingEffect?.let { append(" · анаэробный ${it.oneDecimal()}") }
                activity.trainingLoad?.let { append(" · нагрузка ${it.toInt()}") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (zoneSeconds.any { it > 0.0 }) {
        val includesBelowZoneOne = zoneSeconds.size >= 6
        Text(
            zoneSeconds.mapIndexedNotNull { index, seconds ->
                seconds.takeIf { it > 0.0 }?.let {
                    val label = if (includesBelowZoneOne && index == 0) "ниже Z1" else "Z${index + if (includesBelowZoneOne) 0 else 1}"
                    "$label ${formatActivityDuration(it.roundToLong())}"
                }
            }.joinToString(" · ", prefix = "Пульсовые зоны: "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (activity.configuredMaxHeartRate != null || activity.thresholdHeartRate != null) {
        Text(
            buildString {
                activity.configuredMaxHeartRate?.let { append("Макс. пульс в Garmin ${it.toInt()}") }
                activity.configuredRestingHeartRate?.let { append(" · покой ${it.toInt()}") }
                activity.thresholdHeartRate?.let { append(" · порог ${it.toInt()}") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (
        activity.averageStepLengthMm != null || activity.averageGroundContactTimeMs != null ||
        activity.averageVerticalOscillationMm != null || activity.averageVerticalRatioPercent != null
    ) {
        Text(
            buildString {
                activity.averageStepLengthMm?.let { append("шаг ${twoDecimals(it / 1000)} м  ") }
                activity.averageGroundContactTimeMs?.let { append("контакт ${it.toInt()} мс  ") }
                activity.averageVerticalOscillationMm?.let { append("верт. колебания ${it.oneDecimal()} мм  ") }
                activity.averageVerticalRatioPercent?.let { append("верт. отношение ${it.oneDecimal()}%") }
            }.trim().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (laps.isNotEmpty()) {
        Text("Отрезки", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        laps.forEach { lap ->
            Text(
                buildString {
                    append("${lap.index}. ")
                    lap.distanceMeters?.let { append("${formatDistance(it)} км") }
                    formatPace(lap.durationSeconds, lap.distanceMeters)?.let { append(" · $it/км") }
                    lap.averageHeartRate?.let { append(" · пульс ${it.toInt()}") }
                    lap.averageCadence?.let { append(" · ${it.toInt()} шаг/мин") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatActivityTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))

private fun formatActivityDuration(seconds: Long): String = when {
    seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatPace(durationSeconds: Long, distanceMeters: Double?): String? {
    if (distanceMeters == null || distanceMeters < 100.0) return null
    val secondsPerKm = (durationSeconds * 1000.0 / distanceMeters).toLong()
    return "%d:%02d".format(secondsPerKm / 60, secondsPerKm % 60)
}

private fun formatDistance(distanceMeters: Double): String =
    if (distanceMeters < 100.0) twoDecimals(distanceMeters / 1000.0) else (distanceMeters / 1000.0).oneDecimal()

private fun twoDecimals(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

private inline fun <reified T> decodeMetricList(rawJson: String): List<T> =
    runCatching { Json.decodeFromString<List<T>>(rawJson) }.getOrDefault(emptyList())
