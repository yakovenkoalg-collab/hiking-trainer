package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.yakovenko.mountainform.data.ImportPreview
import ru.yakovenko.mountainform.data.PlanSessionSummary
import ru.yakovenko.mountainform.ui.formatEpochDay

@Composable
fun PlanReviewDialog(preview: ImportPreview, onDismiss: () -> Unit, onApply: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Text("Изменения плана", style = MaterialTheme.typography.headlineSmall)
                Text(preview.plan.author, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        var expanded by rememberSaveable(preview.plan.planId) { mutableStateOf(false) }
                        Text("Добавится ${preview.added} · изменится ${preview.updated} · заменится ${preview.removed}")
                        Text("Выполненная история не меняется.", style = MaterialTheme.typography.bodySmall)
                        if (preview.preservedHistory > 0) Text("Защищены прошлые дни и переносы: ${preview.preservedHistory}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть объяснение" else "Почему меняем план") }
                        if (expanded) {
                            Text(preview.plan.reason)
                            Text("Версия: ${preview.plan.planId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (preview.conflicts.isNotEmpty()) {
                        item {
                            var showConflicts by rememberSaveable(preview.plan.planId) { mutableStateOf(false) }
                            val conflicts = preview.conflicts.distinct()
                            Text("Сначала проверьте ограничения", color = MaterialTheme.colorScheme.error)
                            Text("Применение заблокировано. Ограничения не отключаются автоматически.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { showConflicts = !showConflicts }) {
                                Text(if (showConflicts) "Скрыть причины" else "Показать причины (${conflicts.size})")
                            }
                            if (showConflicts) conflicts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    items(preview.changes.size) { index ->
                        val change = preview.changes.sortedBy { it.after.plannedEpochDay }[index]
                        Card {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (change.before == null) "Добавится" else "Изменится", style = MaterialTheme.typography.labelMedium)
                                change.before?.let { Summary(it, "Было") }
                                Summary(change.after, "План")
                            }
                        }
                    }
                    items(preview.removedSessions.size) { index ->
                        Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Summary(preview.removedSessions[index], "Заменится") } }
                    }
                    if (preview.changes.isEmpty() && preview.removedSessions.isEmpty()) {
                        item { Text("Эти занятия уже в календаре. Применять повторно не нужно.") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(onClick = onApply, modifier = Modifier.weight(1f), enabled = preview.conflicts.isEmpty() &&
                        (preview.changes.isNotEmpty() || preview.removedSessions.isNotEmpty())) { Text("Применить") }
                }
            }
        }
    }
}

@Composable
private fun Summary(summary: PlanSessionSummary, label: String) {
    var expanded by rememberSaveable(summary.plannedEpochDay, summary.title, label) { mutableStateOf(false) }
    Text("$label · ${formatEpochDay(summary.plannedEpochDay)}", style = MaterialTheme.typography.labelMedium)
    Text(summary.title, fontWeight = FontWeight.SemiBold)
    Text("${summary.durationMinutes} мин · RPE ${summary.targetRpe}")
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть упражнения" else "Упражнения (${summary.exercises.size})") }
    if (expanded) summary.exercises.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
}
