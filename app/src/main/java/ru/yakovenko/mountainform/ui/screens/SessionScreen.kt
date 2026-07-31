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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.formatEpochDay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SessionScreen(
    padding: PaddingValues,
    session: TrainingSessionEntity?,
    steps: List<ExerciseStep>,
    shoulderRestrictionActive: Boolean,
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
    var showCompletion by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }
    val completedSteps = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
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
                    Text(session.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(formatEpochDay(session.plannedEpochDay), color = MaterialTheme.colorScheme.primary)
                    Text(session.objective)
                    Text("${session.durationMinutes} мин · целевой RPE ${session.targetRpe}", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (shoulderRestrictionActive) {
                item {
                    SafetyBanner("Плечо: выполняйте только безболезненный диапазон. Не заменяйте шаги на подтягивания, брусья или движения над головой.")
                }
            }
            items(steps.size, key = { steps[it].id }) { index ->
                val step = steps[index]
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = completedSteps[step.id] == true,
                            onCheckedChange = { completedSteps[step.id] = it },
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(step.prescription, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Text(step.instructions, style = MaterialTheme.typography.bodyMedium)
                            if (step.restSeconds > 0) Text("Отдых: ${step.restSeconds} сек", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (session.status == SessionStatus.PLANNED) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showCompletion = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text(" Завершить тренировку")
                        }
                        OutlinedButton(onClick = { showSkip = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Пропустить или перенести")
                        }
                    }
                }
            } else {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (session.status == SessionStatus.COMPLETED) "Тренировка выполнена" else "Тренировка пропущена", fontWeight = FontWeight.Bold)
                            session.actualRpe?.let { Text("Фактический RPE: $it") }
                            if (session.completionNotes.isNotBlank()) Text(session.completionNotes)
                        }
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
private fun NotesDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
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
