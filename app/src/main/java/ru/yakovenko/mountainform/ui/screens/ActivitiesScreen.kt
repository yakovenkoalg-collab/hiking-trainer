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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.ui.formatEpochDay
import ru.yakovenko.mountainform.ui.oneDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ActivitiesScreen(
    activities: List<ImportedActivityEntity>,
    sessions: List<TrainingSessionEntity>,
    onBack: () -> Unit,
    onLinkActivity: (String, String?) -> Unit,
    onIgnoreActivity: (String) -> Unit,
) {
    var linkingActivity by remember { mutableStateOf<ImportedActivityEntity?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фактические тренировки") },
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
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Данные Garmin — это факт выполнения, но приложение не закрывает тренировку плана автоматически. " +
                        "Проверьте совпадение и подтвердите связь.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activities.isEmpty()) {
                item { Card { Text("Активностей пока нет. Обновите Garmin в разделе «Ещё → Garmin и Health Connect».", Modifier.padding(18.dp)) } }
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

@Composable
private fun ActivityCard(
    activity: ImportedActivityEntity,
    linkedSession: TrainingSessionEntity?,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onIgnore: () -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(activity.title, fontWeight = FontWeight.Bold)
            Text("${activity.activityType} · ${formatActivityTime(activity.startAtEpochMillis)} · ${activity.durationSeconds / 60} мин")
            Text(
                buildString {
                    activity.distanceMeters?.let { append("${(it / 1000).oneDecimal()} км  ") }
                    activity.elevationMeters?.let { append("+${it.toInt()} м  ") }
                    activity.averageHeartRate?.let { append("ср. пульс ${it.toInt()}  ") }
                    activity.maxHeartRate?.let { append("макс. ${it.toInt()}  ") }
                    activity.averageCadence?.let { append("каденс ${it.toInt()}  ") }
                    activity.averagePowerWatts?.let { append("${it.toInt()} Вт") }
                }.ifBlank { "Основные метрики отсутствуют в источнике" },
                style = MaterialTheme.typography.bodySmall,
            )
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

private fun formatActivityTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
