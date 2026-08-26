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
import ru.yakovenko.mountainform.data.SessionStatus
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
    onRestoreActivity: (String) -> Unit,
) {
    var linkingActivity by remember { mutableStateOf<ImportedActivityEntity?>(null) }
    var showSources by remember { mutableStateOf(false) }
    val visibleActivities = remember(activities, healthSummary.windowDays) {
        activitiesWithinWindow(activities, healthSummary.windowDays)
    }
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
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Активности · ${healthSummary.windowDays} дн.",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    visibleActivities.count { it.status == ActivityLinkStatus.UNLINKED }.takeIf { it > 0 }?.let {
                        Text("Нужно разобрать: $it", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (visibleActivities.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("За ${healthSummary.windowDays} дней активностей нет", fontWeight = FontWeight.Bold)
                            Text(
                                if (activities.isNotEmpty()) {
                                    "Более старые записи сохранены. Выберите больший период, чтобы их увидеть."
                                } else if (healthSummary.permissionsGranted) {
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
                items(visibleActivities.size, key = { visibleActivities[it].id }) { index ->
                    val activity = visibleActivities[index]
                    ActivityCard(
                        activity = activity,
                        linkedSession = sessions.firstOrNull { it.id == activity.linkedSessionId },
                        onLink = { linkingActivity = activity },
                        onUnlink = { onLinkActivity(activity.id, null) },
                        onIgnore = { onIgnoreActivity(activity.id) },
                        onRestore = { onRestoreActivity(activity.id) },
                    )
                }
            }
        }
    }

    linkingActivity?.let { activity ->
        val activityDay = activityEpochDay(activity)
        val candidates = linkCandidates(activity, sessions, activities)
        AlertDialog(
            onDismissRequest = { linkingActivity = null },
            title = { Text("Связать с тренировкой") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(activity.title, fontWeight = FontWeight.Bold)
                        Text(
                            "Тип: ${friendlyActivityType(activity.activityType)} · ${formatActivityTime(activity.startAtEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Свободные тренировки за 7 дней до и после активности",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (candidates.isEmpty()) {
                        item {
                            Text(
                                "Нет свободных тренировок с ${formatEpochDay(activityDay - LINK_WINDOW_DAYS)} " +
                                    "по ${formatEpochDay(activityDay + LINK_WINDOW_DAYS)}. Уже связанные тренировки скрыты.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(candidates.size, key = { candidates[it].id }) { index ->
                            val session = candidates[index]
                            OutlinedButton(
                                onClick = { onLinkActivity(activity.id, session.id); linkingActivity = null },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${formatEpochDay(session.plannedEpochDay)} · ${session.title}") }
                        }
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
    onRestore: () -> Unit,
) {
    var detailsVisible by remember(activity.id) { mutableStateOf(false) }
    val zoneSeconds = remember(activity.timeInHeartRateZonesJson) {
        decodeMetricList<Double>(activity.timeInHeartRateZonesJson)
    }
    val laps = remember(activity.lapsJson) { decodeMetricList<ActivityLapSummary>(activity.lapsJson) }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(activity.title, fontWeight = FontWeight.Bold)
            Text(
                "Тип: ${friendlyActivityType(activity.activityType)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("${formatActivityTime(activity.startAtEpochMillis)} · ${formatActivityDuration(activity.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
            Text(
                buildString {
                    activity.distanceMeters?.let { append("${formatDistance(it)} км  ") }
                    formatMovementMetric(activity.activityType, activity.durationSeconds, activity.distanceMeters)?.let {
                        append("$it  ")
                    }
                    activity.elevationMeters?.let { append("+${it.toInt()} м  ") }
                    activity.descentMeters?.let { append("−${it.toInt()} м") }
                }.ifBlank { activity.activityType },
                style = MaterialTheme.typography.bodySmall,
            )
            val secondaryMetrics = buildString {
                    activity.averageHeartRate?.let { append("пульс ${it.toInt()}") }
                    activity.maxHeartRate?.let { append(" / ${it.toInt()}  ") }
                    activity.averageCadence?.let {
                        append("каденс ${it.toInt()} ${cadenceUnit(activity.activityType)}  ")
                    }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Игнорируется", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRestore) { Text("Вернуть к привязке") }
                }
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
                    formatMovementMetric(activity.activityType, lap.durationSeconds, lap.distanceMeters)?.let {
                        append(" · $it")
                    }
                    lap.averageHeartRate?.let { append(" · пульс ${it.toInt()}") }
                    lap.averageCadence?.let { append(" · ${it.toInt()} ${cadenceUnit(activity.activityType)}") }
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

private fun activityEpochDay(activity: ImportedActivityEntity): Long =
    Instant.ofEpochMilli(activity.startAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()

internal fun linkCandidates(
    activity: ImportedActivityEntity,
    sessions: List<TrainingSessionEntity>,
    activities: List<ImportedActivityEntity>,
): List<TrainingSessionEntity> {
    val activityDay = activityEpochDay(activity)
    val occupiedSessionIds = activities.asSequence()
        .filter { it.id != activity.id }
        .mapNotNull { it.linkedSessionId }
        .toSet()
    return sessions
        .asSequence()
        .filter { abs(it.plannedEpochDay - activityDay) <= LINK_WINDOW_DAYS }
        .filterNot { it.status == SessionStatus.SKIPPED }
        .filterNot { it.id in occupiedSessionIds }
        .sortedWith(compareBy<TrainingSessionEntity> { it.plannedEpochDay }.thenBy { it.title })
        .toList()
}

internal fun friendlyActivityType(rawType: String): String {
    val type = rawType.trim()
    val normalized = normalizeActivityType(type)
    return when {
        isCycling(normalized) -> "Велосипед"
        isPaceActivity(normalized) && ("WALK" in normalized || "ХОДЬБ" in normalized) -> "Ходьба"
        isPaceActivity(normalized) && ("HIK" in normalized || "MOUNTAINEER" in normalized || "ПОХОД" in normalized) -> "Поход"
        isPaceActivity(normalized) -> "Бег"
        "SWIM" in normalized || "ПЛАВАН" in normalized -> "Плавание"
        "ROW" in normalized || "ГРЕБ" in normalized -> "Гребля"
        "SKI" in normalized || "ЛЫЖ" in normalized -> "Лыжи"
        "ELLIPTICAL" in normalized -> "Эллипс"
        normalized in setOf(
            "STRENGTH_TRAINING",
            "TRAINING",
            "FITNESS_EQUIPMENT",
            "СИЛОВАЯ",
            "СИЛОВАЯ ТРЕНИРОВКА",
        ) -> "Силовая тренировка"
        else -> type.ifBlank { "Не определён" }.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

private fun formatActivityDuration(seconds: Long): String = when {
    seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatPace(durationSeconds: Long, distanceMeters: Double?): String? {
    if (durationSeconds <= 0 || distanceMeters == null || distanceMeters < 100.0) return null
    val secondsPerKm = (durationSeconds * 1000.0 / distanceMeters).toLong()
    return "%d:%02d".format(secondsPerKm / 60, secondsPerKm % 60)
}

private fun formatAverageSpeed(durationSeconds: Long, distanceMeters: Double?): String? {
    if (durationSeconds <= 0 || distanceMeters == null || distanceMeters < 100.0) return null
    return "${(distanceMeters * 3.6 / durationSeconds).oneDecimal()} км/ч"
}

internal fun formatMovementMetric(activityType: String, durationSeconds: Long, distanceMeters: Double?): String? {
    val normalized = normalizeActivityType(activityType)
    return when {
        isCycling(normalized) -> formatAverageSpeed(durationSeconds, distanceMeters)
        isPaceActivity(normalized) -> formatPace(durationSeconds, distanceMeters)?.let { "$it/км" }
        else -> null
    }
}

internal fun cadenceUnit(activityType: String): String {
    val normalized = normalizeActivityType(activityType)
    return when {
        isCycling(normalized) -> "об/мин"
        isPaceActivity(normalized) -> "шаг/мин"
        "SWIM" in normalized || "ROW" in normalized || "ПЛАВАН" in normalized || "ГРЕБ" in normalized -> "гребков/мин"
        else -> "об/мин"
    }
}

private fun normalizeActivityType(activityType: String): String =
    activityType.trim().uppercase(java.util.Locale.ROOT)

private fun isCycling(normalizedType: String): Boolean =
    "BIK" in normalizedType || "CYCL" in normalizedType || "ВЕЛОСИПЕД" in normalizedType

private fun isPaceActivity(normalizedType: String): Boolean =
    "RUN" in normalizedType || "WALK" in normalizedType || "HIK" in normalizedType ||
        "MOUNTAINEER" in normalizedType || "БЕГ" in normalizedType || "ХОДЬБ" in normalizedType ||
        "ПОХОД" in normalizedType

private fun formatDistance(distanceMeters: Double): String =
    if (distanceMeters < 100.0) twoDecimals(distanceMeters / 1000.0) else (distanceMeters / 1000.0).oneDecimal()

private fun twoDecimals(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

private inline fun <reified T> decodeMetricList(rawJson: String): List<T> =
    runCatching { Json.decodeFromString<List<T>>(rawJson) }.getOrDefault(emptyList())

private const val LINK_WINDOW_DAYS = 7L
