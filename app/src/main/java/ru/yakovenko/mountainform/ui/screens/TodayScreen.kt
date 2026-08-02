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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.ReadinessPill
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.formatEpochDay

@Composable
fun TodayScreen(
    padding: PaddingValues,
    state: AppUiState,
    onSaveReadiness: (Int, Int, Int, Int, Int, Int, Boolean, String) -> Unit,
    onOpenSession: (String) -> Unit,
    onCompleteCorePractice: () -> Unit,
    onShareReviewReport: () -> Unit,
    openReadiness: Boolean = false,
    onReadinessOpened: () -> Unit = {},
) {
    var showCheck by remember { mutableStateOf(false) }
    val decision = state.readinessDecision
    val session = state.nextSession

    LaunchedEffect(openReadiness) {
        if (openReadiness) {
            showCheck = true
            onReadinessOpened()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Горная форма", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    state.profile?.currentPhase ?: "Подготовка профиля…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.profile?.shoulderRestrictionActive == true) {
            item {
                SafetyBanner(
                    "Активное ограничение: резкая боль в левом плече при отведении. " +
                        "Не выполнять болезненные движения, жимы над головой, подтягивания и брусья до оценки специалистом.",
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Готовность сегодня",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    ReadinessPill(decision.level, decision.title)
                    if (decision.reasons.isNotEmpty()) {
                        Text(
                            "Учтено: ${decision.reasons.joinToString("; ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(decision.recommendation, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showCheck = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.todayCheck == null) "Отметить состояние" else "Обновить состояние")
                    }
                }
            }
        }
        item { SectionTitle("Следующая тренировка") }
        item {
            if (session == null) {
                Card { Text("Запланированных тренировок пока нет", Modifier.padding(18.dp)) }
            } else {
                Card(onClick = { onOpenSession(session.id) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (session.type == "RUN") Icons.AutoMirrored.Filled.DirectionsRun else Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(formatEpochDay(session.plannedEpochDay), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                        Text(session.objective, style = MaterialTheme.typography.bodyMedium)
                        Text("${session.durationMinutes} мин · целевой RPE ${session.targetRpe}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        state.reviewCheckpoints.firstOrNull { it.status != ru.yakovenko.mountainform.data.ReviewStatus.RESOLVED }?.let { checkpoint ->
            item { SectionTitle("Контрольная точка плана") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(checkpoint.reason, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Сформируйте отчёт, отправьте его в этот чат и импортируйте предложенный JSON только после просмотра изменений.")
                        Button(onClick = onShareReviewReport, modifier = Modifier.fillMaxWidth()) {
                            Text(if (checkpoint.status == ru.yakovenko.mountainform.data.ReviewStatus.EXPORTED) "Поделиться отчётом снова" else "Сформировать и поделиться")
                        }
                    }
                }
            }
        }
        item { SectionTitle("Core и осанка", "Короткая практика не считается отдельной тренировкой") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("10 минут: контроль корпуса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Дыхание и положение рёбер · контроль таза · мягкая подвижность грудного отдела. Только без боли в плече.")
                    Text("Рекомендуется 3–4 раза в неделю", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val doneToday = state.practices.any { it.epochDay == java.time.LocalDate.now().toEpochDay() && it.type == "CORE_POSTURE" }
                    OutlinedButton(
                        onClick = onCompleteCorePractice,
                        enabled = !doneToday,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (doneToday) "Сегодня выполнено" else "Отметить 10 минут") }
                }
            }
        }
        state.goals.firstOrNull { it.type == GoalType.RUNNING }?.let { goal ->
            item { SectionTitle("Весенняя беговая цель") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Базовый вариант — полумарафон. Марафон рассматриваем после 8–12 недель устойчивого бегового объёма.")
                        Text("Сейчас: диагностика лёгкого бега", style = MaterialTheme.typography.labelLarge)
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
