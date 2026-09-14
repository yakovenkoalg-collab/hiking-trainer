package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal val recordingDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
internal fun parseRecordingDay(text: String): Long? = runCatching {
    LocalDate.parse(text, recordingDateFormat).takeIf { !it.isAfter(LocalDate.now()) && it.year >= 2000 }?.toEpochDay()
}.getOrNull()

@Composable
internal fun RecordingDateField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text("Дата занятия") },
        supportingText = { Text("ДД.ММ.ГГГГ · не дата заполнения отчёта") },
        isError = parseRecordingDay(value) == null,
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("performed_date"),
    )
}

@Composable
internal fun RetrospectiveSessionDialog(
    sessionId: String,
    targets: List<WorkoutSetTarget>,
    existingLogs: List<SessionSetLogEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Int, SessionCompletionDetails) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().format(recordingDateFormat)) }
    var duration by rememberSaveable { mutableStateOf("") }
    var rpe by rememberSaveable { mutableFloatStateOf(3f) }
    var notes by rememberSaveable { mutableStateOf("") }
    val minutes = duration.toIntOrNull()?.takeIf { it in 1..720 }
    val day = parseRecordingDay(date)
    fun isRecorded(target: WorkoutSetTarget) = existingLogs.any {
        it.stepId == target.step.id && it.roundIndex == target.roundIndex && it.setIndex == target.setIndex && it.completed
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp)) {
                Text("Уже выполнил", style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false).testTag("retrospective_content"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Отметьте выполненные подходы. Секундомер и отдых не записываются; прежние результаты сохранятся.", style = MaterialTheme.typography.bodySmall) }
                    item { RecordingDateField(date) { date = it } }
                    item {
                        OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) },
                            label = { Text("Фактическое время, мин") },
                            supportingText = { Text("Вся тренировка, включая отдых · 1–720 мин") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("actual_minutes"))
                    }
                    item {
                        Text("Нагрузка: ${rpe.toInt()}/10 RPE")
                        Slider(rpe, { rpe = it }, valueRange = 1f..10f, steps = 8)
                    }
                    itemsIndexed(targets) { index, target ->
                        val recorded = isRecorded(target)
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(recorded || index in selected, onCheckedChange = {
                                selected = if (it) selected + index else selected - index
                            }, enabled = !recorded, modifier = Modifier.testTag("retro_set_$index"))
                            Column(Modifier.weight(1f)) {
                                Text(target.step.title)
                                Text(buildString {
                                    append(if (target.totalRounds > 1) "Круг ${target.roundIndex}" else "Подход ${target.setIndex}")
                                    append(" · ${target.doseLabel()}")
                                    if (recorded) append(" · уже записан")
                                }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item { OutlinedTextField(notes, { notes = it }, label = { Text("Изменения, самочувствие, боль") }, modifier = Modifier.fillMaxWidth()) }
                }
                Button(onClick = {
                    val now = System.currentTimeMillis()
                    onConfirm(rpe.toInt(), notes, requireNotNull(minutes) * 60,
                        SessionCompletionDetails(requireNotNull(day), "RETROSPECTIVE", selected.sorted().map {
                            retrospectiveSetLog(sessionId, targets[it], now)
                        }))
                }, enabled = minutes != null && day != null && (selected.isNotEmpty() || targets.any(::isRecorded)),
                    modifier = Modifier.fillMaxWidth().testTag("save_retrospective")) { Text("Сохранить занятие") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            }
        }
    }
}
