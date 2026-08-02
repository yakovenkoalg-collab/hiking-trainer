package ru.yakovenko.mountainform.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.R
import ru.yakovenko.mountainform.data.ExerciseCatalogEntity
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.data.SessionStepLogEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.catalogId
import ru.yakovenko.mountainform.data.imageKey
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.formatEpochDay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SessionScreen(
    padding: PaddingValues,
    session: TrainingSessionEntity?,
    steps: List<ExerciseStep>,
    catalog: List<ExerciseCatalogEntity>,
    stepLogs: List<SessionStepLogEntity>,
    shoulderRestrictionActive: Boolean,
    loadBlocked: Boolean,
    adaptationRequired: Boolean,
    readinessRecommendation: String,
    onStepCompleted: (String, String, Boolean) -> Unit,
    onBack: () -> Unit,
    onComplete: (String, Int, String) -> Unit,
    onSkip: (String, String) -> Unit,
) {
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("Тренировка не найдена")
            TextButton(onClick = onBack) { Text("Назад") }
        }
        return
    }

    var stepIndex by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var showCompletion by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }
    var showPainStop by remember { mutableStateOf(false) }
    var restSeconds by rememberSaveable(session.id) { mutableIntStateOf(0) }
    val completedIds = stepLogs.filter { it.sessionId == session.id && it.completed }.mapTo(mutableSetOf()) { it.stepId }
    val completedCount = steps.count { it.id in completedIds }
    val step = steps.getOrNull(stepIndex)
    val details = step?.let { current -> catalog.firstOrNull { it.id == current.catalogId() } }

    LaunchedEffect(restSeconds) {
        if (restSeconds > 0) {
            delay(1_000)
            restSeconds -= 1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatEpochDay(session.plannedEpochDay), color = MaterialTheme.colorScheme.primary)
                        Text("${session.durationMinutes} мин · RPE ${session.targetRpe}")
                    }
                    Text(session.objective, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { if (steps.isEmpty()) 0f else completedCount.toFloat() / steps.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Выполнено $completedCount из ${steps.size}", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (shoulderRestrictionActive) {
                item {
                    SafetyBanner("Плечо: только безболезненный диапазон. Не добавляйте подтягивания, брусья и движения над головой.")
                }
            }
            if (loadBlocked) {
                item {
                    SafetyBanner("Тренировочная нагрузка заблокирована по сегодняшней оценке. $readinessRecommendation")
                }
            } else if (adaptationRequired) {
                item {
                    SafetyBanner("Сегодня нужна адаптация. $readinessRecommendation Приложение не заменяет упражнение автоматически.")
                }
            }

            step?.let { current ->
                item {
                    Text(
                        "Шаг ${stepIndex + 1} из ${steps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(current.prescription, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                item { ExerciseIllustration(current.imageKey()) }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            details?.setup?.takeIf { it.isNotBlank() }?.let { Instruction("Подготовка", it) }
                            Instruction("Выполнение", details?.execution ?: current.instructions)
                            details?.breathing?.takeIf { it.isNotBlank() }?.let { Instruction("Дыхание", it) }
                            if (details == null && current.instructions.isNotBlank()) {
                                Text(current.instructions)
                            }
                            if (current.restSeconds > 0) Text("Отдых: ${current.restSeconds} сек", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                details?.let { catalogItem ->
                    item {
                        val mistakes = remember(catalogItem.commonMistakesJson) {
                            runCatching { Json.decodeFromString<List<String>>(catalogItem.commonMistakesJson) }.getOrDefault(emptyList())
                        }
                        if (mistakes.isNotEmpty()) {
                            Card {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Частые ошибки", fontWeight = FontWeight.Bold)
                                    mistakes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
                if (restSeconds > 0) {
                    item {
                        Card {
                            Column(
                                Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Отдых", fontWeight = FontWeight.Bold)
                                Text("$restSeconds сек", style = MaterialTheme.typography.headlineMedium)
                                TextButton(onClick = { restSeconds = 0 }) { Text("Пропустить таймер") }
                            }
                        }
                    }
                }
                if (session.status == SessionStatus.PLANNED) {
                    item {
                        Button(
                            onClick = {
                                val done = current.id !in completedIds
                                onStepCompleted(session.id, current.id, done)
                                if (done) restSeconds = current.restSeconds
                            },
                            enabled = !loadBlocked,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text(if (current.id in completedIds) "  Отменить отметку" else "  Отметить выполненным")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { if (stepIndex > 0) stepIndex -= 1 },
                                enabled = stepIndex > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text("Назад") }
                            OutlinedButton(
                                onClick = {
                                    if (stepIndex < steps.lastIndex) stepIndex += 1 else showCompletion = true
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                                Text(if (stepIndex == steps.lastIndex) "  Итог" else "  Далее")
                            }
                        }
                        OutlinedButton(onClick = { showPainStop = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Возникла боль — остановиться")
                        }
                    }
                }
            }

            if (session.status != SessionStatus.PLANNED) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (session.status == SessionStatus.COMPLETED) "Тренировка выполнена" else "Тренировка остановлена или пропущена",
                                fontWeight = FontWeight.Bold,
                            )
                            session.actualRpe?.let { Text("Фактический RPE: $it") }
                            if (session.completionNotes.isNotBlank()) Text(session.completionNotes)
                        }
                    }
                }
            } else {
                item {
                    TextButton(onClick = { showSkip = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Пропустить тренировку")
                    }
                }
            }
        }
    }

    if (showCompletion) {
        CompletionDialog(
            initialRpe = session.targetRpe,
            onDismiss = { showCompletion = false },
            onConfirm = { rpe, notes -> onComplete(session.id, rpe, notes) },
        )
    }
    if (showSkip) {
        NotesDialog(
            title = "Почему тренировка пропущена?",
            confirmLabel = "Сохранить",
            onDismiss = { showSkip = false },
            onConfirm = { onSkip(session.id, it) },
        )
    }
    if (showPainStop) {
        AlertDialog(
            onDismissRequest = { showPainStop = false },
            title = { Text("Остановите упражнение") },
            text = {
                Text(
                    "Не пытайтесь «разработать» резкую или нарастающую боль. При опухоли, блокировке, нестабильности, " +
                        "нарастающей ночной боли или слабости обратитесь к врачу.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onSkip(session.id, "Тренировка остановлена из-за боли") }) { Text("Завершить и сохранить") }
            },
            dismissButton = { TextButton(onClick = { showPainStop = false }) { Text("Пауза") } },
        )
    }
}

@Composable
private fun ExerciseIllustration(key: String) {
    val resource = illustrationResource(key)
    Card {
        if (resource != null) {
            Image(
                painter = painterResource(resource),
                contentDescription = "Последовательность выполнения упражнения",
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Схема готовится", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@DrawableRes
private fun illustrationResource(key: String): Int? = when (key) {
    "breathing" -> R.drawable.exercise_breathing
    "heel-slide" -> R.drawable.exercise_heel_slide
    "thoracic-mobility", "mobility" -> R.drawable.exercise_thoracic_mobility
    "glute-bridge", "bridge" -> R.drawable.exercise_glute_bridge
    "box-squat" -> R.drawable.exercise_box_squat
    "hip-hinge", "hinge" -> R.drawable.exercise_hip_hinge
    "calf-raise", "calf" -> R.drawable.exercise_calf_raise
    "dead-bug-legs", "core" -> R.drawable.exercise_dead_bug_legs
    "stationary-bike", "bike" -> R.drawable.exercise_stationary_bike
    "walk", "aerobic" -> R.drawable.exercise_walk
    "run-walk" -> R.drawable.exercise_run_walk
    "side-core" -> R.drawable.exercise_side_core
    else -> null
}

@Composable
private fun Instruction(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(text)
    }
}

@Composable
private fun CompletionDialog(initialRpe: Int, onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var rpe by remember { mutableFloatStateOf(initialRpe.toFloat()) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Итог тренировки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Фактический RPE: ${rpe.toInt()}/10")
                Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Самочувствие, боль, комментарий") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rpe.toInt(), notes) }) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NotesDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Причина") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(notes) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
