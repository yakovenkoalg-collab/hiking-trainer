package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal val recordingDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
/** Date and duration of a workout taken from its linked Garmin records. */
internal data class GarminPrefill(val epochDay: Long, val minutes: Int)

internal fun garminPrefill(
    activities: List<ImportedActivityEntity>,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): GarminPrefill? {
    val first = activities.minByOrNull { it.startAtEpochMillis } ?: return null
    val seconds = activities.sumOf { it.durationSeconds.coerceAtLeast(0) }
    if (seconds <= 0) return null
    val day = java.time.Instant.ofEpochMilli(first.startAtEpochMillis).atZone(zone).toLocalDate()
    return GarminPrefill(day.toEpochDay(), ((seconds + 30) / 60).toInt().coerceIn(1, 720))
}

internal fun parseRecordingDay(text: String): Long? = runCatching {
    LocalDate.parse(text, recordingDateFormat).takeIf { !it.isAfter(LocalDate.now()) && it.year >= 2000 }?.toEpochDay()
}.getOrNull()

@Composable
internal fun RecordingDateField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text("Дата занятия") },
        supportingText = { Text("ДД.ММ.ГГГГ") },
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
    prefill: GarminPrefill? = null,
    initialRpe: Int = 3,
) {
    fun isRecorded(target: WorkoutSetTarget) = existingLogs.any {
        it.stepId == target.step.id && it.roundIndex == target.roundIndex && it.setIndex == target.setIndex && it.completed
    }
    var selected by remember {
        mutableStateOf(if (prefill != null) targets.indices.filterNot { isRecorded(targets[it]) }.toSet() else emptySet())
    }
    var date by rememberSaveable {
        mutableStateOf((prefill?.let { LocalDate.ofEpochDay(it.epochDay) } ?: LocalDate.now()).format(recordingDateFormat))
    }
    var duration by rememberSaveable { mutableStateOf(prefill?.minutes?.toString().orEmpty()) }
    var rpe by rememberSaveable { mutableFloatStateOf(initialRpe.coerceIn(1, 10).toFloat()) }
    var notes by rememberSaveable { mutableStateOf("") }
    val minutes = duration.toIntOrNull()?.takeIf { it in 1..720 }
    val day = parseRecordingDay(date)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp)) {
                Text(if (prefill != null) "Засчитать по Garmin" else "Уже выполнил", style = MaterialTheme.typography.titleLarge)
                if (prefill != null) {
                    Text(
                        "Дата, время и этапы заполнены по записи Garmin — проверьте и оцените нагрузку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false).testTag("retrospective_content"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { RecordingDateField(date) { date = it } }
                    item {
                        OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) },
                            label = { Text("Фактическое время, мин") },
                            supportingText = { Text("Вместе с отдыхом") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("actual_minutes"))
                    }
                    item {
                        Text("Нагрузка: ${rpe.toInt()}/10 RPE")
                        Slider(rpe, { rpe = it }, valueRange = 1f..10f, steps = 8)
                    }
                    item {
                        val open = targets.indices.filter { !isRecorded(targets[it]) }
                        val allSelected = open.isNotEmpty() && selected.containsAll(open)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Что выполнено", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { selected = if (allSelected) emptySet() else open.toSet() },
                                enabled = open.isNotEmpty(),
                                modifier = Modifier.testTag("retro_select_all"),
                            ) { Text(if (allSelected) "Снять все" else "Отметить все") }
                        }
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
                                    when {
                                        target.totalRounds > 1 -> append("Круг ${target.roundIndex} · ")
                                        target.totalSets > 1 -> append("Подход ${target.setIndex} · ")
                                    }
                                    append(target.doseLabel())
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
