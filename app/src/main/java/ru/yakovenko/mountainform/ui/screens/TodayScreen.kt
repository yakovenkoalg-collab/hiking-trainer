package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.domain.ReadinessLevel
import ru.yakovenko.mountainform.data.ShoulderLoadPhase
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.ReadinessPill
import ru.yakovenko.mountainform.ui.formatEpochDay
import ru.yakovenko.mountainform.ui.formatLongEpochDay

@Composable
fun TodayScreen(
    padding: PaddingValues,
    state: AppUiState,
    onSaveReadiness: (Int, Int, Int, Int, Int, Int, Boolean, String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenCalendar: () -> Unit = {},
    onCompleteCorePractice: () -> Unit,
    onUndoCorePractice: () -> Unit = {},
    onShareReviewReport: () -> Unit,
    openReadiness: Boolean = false,
    onReadinessOpened: () -> Unit = {},
    onOpenShoulderSettings: () -> Unit = {},
) {
    var showCheck by remember { mutableStateOf(false) }
    var showShoulderDetails by remember { mutableStateOf(false) }
    val decision = state.readinessDecision
    val session = state.nextSession
    val today = java.time.LocalDate.now().toEpochDay()
    val doneToday = state.practices.any {
        it.epochDay == today && it.type == "CORE_POSTURE"
    }
    val shoulderPhaseLabel = when (state.profile?.shoulderLoadPhase) {
        ShoulderLoadPhase.THERAPIST_CLEARED -> "разрешённый комплекс"
        ShoulderLoadPhase.RETURNING -> "возврат силы"
        else -> "ограничено"
    }

    LaunchedEffect(openReadiness) {
        if (openReadiness) {
            showCheck = true
            onReadinessOpened()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Горная форма", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatLongEpochDay(today),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Состояние на сегодня",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).testTag("readiness_title"),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { showCheck = true }) {
                            Text(if (state.todayCheck == null) "Отметить" else "Изменить")
                        }
                    }
                    ReadinessPill(
                        decision.level,
                        if (state.todayCheck == null) "Готовность не отмечена" else decision.title,
                    )
                    if (decision.level != ReadinessLevel.GREEN) {
                        Text(
                            decision.recommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    if (state.profile?.shoulderRestrictionActive == true) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { showShoulderDetails = true }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Плечо: $shoulderPhaseLabel",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                )
                                Text(
                                    "Рекомендации и этап нагрузки",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            if (session == null) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Следующих тренировок пока нет", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть календарь и обновить план")
                        }
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (session.type == "RUN") Icons.AutoMirrored.Filled.DirectionsRun else Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (session.plannedEpochDay == today) "Тренировка на сегодня" else "Следующая тренировка",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            session.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${formatEpochDay(session.plannedEpochDay)} · ${session.durationMinutes} мин · RPE ${session.targetRpe}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onOpenSession(session.id) },
                            modifier = Modifier.fillMaxWidth().testTag("open_next_session"),
                        ) {
                            Text("Открыть тренировку")
                        }
                    }
                }
            }
        }
        item { Text("На сегодня", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (doneToday) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Core и осанка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("10 минут · без боли в плече", style = MaterialTheme.typography.bodySmall)
                    }
                    if (doneToday) {
                        TextButton(onClick = onUndoCorePractice) { Text("Отменить") }
                    } else {
                        TextButton(onClick = onCompleteCorePractice) {
                            Text("Отметить")
                        }
                    }
                }
            }
        }
        state.reviewCheckpoints.firstOrNull { it.status != ru.yakovenko.mountainform.data.ReviewStatus.RESOLVED }?.let { checkpoint ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("План нужно обновить", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(checkpoint.reason, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        OutlinedButton(onClick = onShareReviewReport, modifier = Modifier.fillMaxWidth()) {
                            Text("Сформировать отчёт")
                        }
                    }
                }
            }
        }
    }

    if (showCheck) {
        ReadinessDialog(
            current = state.todayCheck,
            onDismiss = { showCheck = false },
            onSave = { sleep, energy, fatigue, soreness, shoulder, knee, illness, notes ->
                onSaveReadiness(sleep, energy, fatigue, soreness, shoulder, knee, illness, notes)
                showCheck = false
            },
        )
    }
    if (showShoulderDetails) {
        AlertDialog(
            onDismissRequest = { showShoulderDetails = false },
            title = { Text("Ограничение для плеча") },
            text = {
                Text(
                    "Работайте только в безболезненном диапазоне. Не выполняйте болезненные движения, " +
                        "жимы над головой, подтягивания и брусья до повторной оценки специалистом.",
                )
            },
            confirmButton = { TextButton(onClick = { showShoulderDetails = false }) { Text("Понятно") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        showShoulderDetails = false
                        onOpenShoulderSettings()
                    },
                ) { Text("Настроить этап") }
            },
        )
    }
}

@Composable
private fun ReadinessDialog(
    current: ru.yakovenko.mountainform.data.ReadinessCheckEntity?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int, Int, Int, Boolean, String) -> Unit,
) {
    var sleep by remember { mutableIntStateOf(current?.sleep ?: 3) }
    var energy by remember { mutableIntStateOf(current?.energy ?: 3) }
    var fatigue by remember { mutableIntStateOf(current?.fatigue ?: 3) }
    var soreness by remember { mutableIntStateOf(current?.soreness ?: 2) }
    var shoulder by remember { mutableFloatStateOf((current?.shoulderPain ?: 0).toFloat()) }
    var knee by remember { mutableFloatStateOf((current?.kneePain ?: 0).toFloat()) }
    var illness by remember { mutableStateOf(current?.illness ?: false) }
    var notes by remember { mutableStateOf(current?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Состояние перед тренировкой") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { RatingRow("Сон", sleep, "1 — очень плохо, 5 — отлично") { sleep = it } }
                item { RatingRow("Энергия", energy, "1 — очень низкая, 5 — высокая") { energy = it } }
                item { RatingRow("Усталость", fatigue, "1 — нет, 5 — сильная") { fatigue = it } }
                item { RatingRow("Мышечная болезненность", soreness, "1 — нет, 5 — сильная") { soreness = it } }
                item {
                    PainSlider("Левое плечо", shoulder) { shoulder = it }
                }
                item {
                    PainSlider("Правое колено", knee) { knee = it }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = illness, onCheckedChange = { illness = it })
                        Text("Есть признаки болезни")
                    }
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Комментарий") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(sleep, energy, fatigue, soreness, shoulder.toInt(), knee.toInt(), illness, notes) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun RatingRow(label: String, value: Int, hint: String, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label: $value", style = MaterialTheme.typography.labelLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { rating ->
                FilterChip(
                    selected = value == rating,
                    onClick = { onChange(rating) },
                    label = { Text(rating.toString()) },
                )
            }
        }
    }
}

@Composable
private fun PainSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label — боль ${value.toInt()}/10", style = MaterialTheme.typography.labelLarge)
        Text("0 — нет боли, 10 — максимально сильная", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = 0f..10f, steps = 9)
    }
}
