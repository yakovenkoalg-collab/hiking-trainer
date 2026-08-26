package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.formatEpochDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    padding: PaddingValues,
    state: AppUiState,
    onOpenSession: (String) -> Unit,
    onReschedule: (String, Long, String) -> Unit,
    onProposeNextBlock: () -> Unit,
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var sessionToMove by remember { mutableStateOf<TrainingSessionEntity?>(null) }
    val sessionsByDay = state.sessions.groupBy { it.plannedEpochDay }
    val selectedSessions = sessionsByDay[selectedDate.toEpochDay()].orEmpty()
    val selectedCompleted = selectedSessions.count { it.status == SessionStatus.COMPLETED }
    val selectedPlanned = selectedSessions.count { it.status == SessionStatus.PLANNED }
    val selectedSkipped = selectedSessions.count { it.status == SessionStatus.SKIPPED }
    val futurePlanned = state.sessions.count {
        it.status == SessionStatus.PLANNED && it.plannedEpochDay >= LocalDate.now().toEpochDay()
    }
    val lastPlannedDay = state.sessions.filter { it.status == SessionStatus.PLANNED }
        .maxOfOrNull { it.plannedEpochDay } ?: LocalDate.now().toEpochDay()
    val bufferDays = lastPlannedDay - LocalDate.now().toEpochDay()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Календарь") }
        item {
            MonthCalendar(
                month = selectedMonth,
                selectedDate = selectedDate,
                sessionsByDay = sessionsByDay,
                onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                onSelectDate = { selectedDate = it },
            )
        }
        item {
            SectionTitle(
                selectedDate.dayOfMonth.toString() + " " +
                    selectedDate.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru")),
                if (selectedSessions.isEmpty()) {
                    "Тренировок нет"
                } else {
                    buildList {
                        if (selectedCompleted > 0) add("$selectedCompleted выполнено")
                        if (selectedPlanned > 0) add("$selectedPlanned запланировано")
                        if (selectedSkipped > 0) add("$selectedSkipped пропущено")
                    }.joinToString(" · ")
                },
            )
        }
        if (selectedSessions.isEmpty()) {
            item {
                Card {
                    Text("Свободный день", Modifier.padding(16.dp))
                }
            }
        } else {
            items(selectedSessions.size, key = { selectedSessions[it].id }) { index ->
                val session = selectedSessions[index]
                CalendarSessionCard(
                    session = session,
                    onOpen = { onOpenSession(session.id) },
                    onMove = { sessionToMove = session },
                )
            }
        }
        if (state.rescheduleEvents.isNotEmpty()) {
            item { SectionTitle("Последние переносы") }
            items(state.rescheduleEvents.take(2).size) { index ->
                val event = state.rescheduleEvents[index]
                Text(
                    "${formatEpochDay(event.fromEpochDay)} → ${formatEpochDay(event.toEpochDay)}" +
                        event.reason.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (bufferDays <= 14) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("План до ${formatEpochDay(lastPlannedDay)}", fontWeight = FontWeight.Bold)
                        Text("$futurePlanned предстоящих тренировок", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onProposeNextBlock, modifier = Modifier.fillMaxWidth()) {
                            Text("Обновить план")
                        }
                    }
                }
            }
        }
    }

    sessionToMove?.let { session ->
        RescheduleDialog(
            session = session,
            onDismiss = { sessionToMove = null },
            onConfirm = { date, reason ->
                onReschedule(session.id, date.toEpochDay(), reason)
                selectedMonth = YearMonth.from(date)
                selectedDate = date
                sessionToMove = null
            },
        )
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    sessionsByDay: Map<Long, List<TrainingSessionEntity>>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Card {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий месяц")
                }
                Text(
                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() } +
                        " ${month.year}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий месяц")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val first = month.atDay(1)
            val leading = first.dayOfWeek.value - 1
            val cells = leading + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            repeat(rows) { rowIndex ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { columnIndex ->
                        val dayNumber = rowIndex * 7 + columnIndex - leading + 1
                        if (dayNumber !in 1..month.lengthOfMonth()) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(dayNumber)
                            val daySessions = sessionsByDay[date.toEpochDay()].orEmpty()
                            CalendarDay(
                                date = date,
                                selected = date == selectedDate,
                                today = date == LocalDate.now(),
                                sessions = daySessions,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectDate(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    sessions: List<TrainingSessionEntity>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.aspectRatio(1f).padding(2.dp).clickable(onClick = onClick),
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            today -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                sessions.take(3).forEach { session ->
                    if (session.status == SessionStatus.COMPLETED) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Тренировка выполнена",
                            tint = sessionColor(session),
                            modifier = Modifier.size(11.dp),
                        )
                    } else {
                        Box(Modifier.size(6.dp).background(sessionColor(session), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSessionCard(
    session: TrainingSessionEntity,
    onOpen: () -> Unit,
    onMove: () -> Unit,
) {
    Card(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (session.status == SessionStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.FitnessCenter,
                contentDescription = if (session.status == SessionStatus.COMPLETED) "Выполнено" else null,
                tint = sessionColor(session),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.title, fontWeight = FontWeight.Bold)
                Text("${session.durationMinutes} мин · RPE ${session.targetRpe}")
                Text(
                    when (session.status) {
                        SessionStatus.COMPLETED -> "Выполнено"
                        SessionStatus.SKIPPED -> "Пропущено"
                        else -> "Запланировано"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (session.status == SessionStatus.PLANNED) {
                IconButton(onClick = onMove) {
                    Icon(Icons.Default.EventRepeat, contentDescription = "Перенести")
                }
            }
        }
    }
}

@Composable
private fun sessionColor(session: TrainingSessionEntity): Color = when {
    session.status == SessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    session.status == SessionStatus.SKIPPED -> MaterialTheme.colorScheme.outline
    session.type == "RUN" -> MaterialTheme.colorScheme.tertiary
    session.type == "RECOVERY" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun RescheduleDialog(
    session: TrainingSessionEntity,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, String) -> Unit,
) {
    var selectedDate by remember(session.id) { mutableStateOf(LocalDate.ofEpochDay(session.plannedEpochDay)) }
    var selectedMonth by remember(session.id) { mutableStateOf(YearMonth.from(selectedDate)) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перенести тренировку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(session.title)
                MonthCalendar(
                    month = selectedMonth,
                    selectedDate = selectedDate,
                    sessionsByDay = emptyMap(),
                    onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                    onSelectDate = { selectedDate = it },
                )
                Text(
                    "Новая дата: ${formatEpochDay(selectedDate.toEpochDay())}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Причина переноса") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDate, reason.ifBlank { "Перенос пользователем" }) },
            ) { Text("Перенести") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
