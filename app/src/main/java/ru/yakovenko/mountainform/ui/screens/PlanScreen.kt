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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.formatEpochDay

@Composable
fun PlanScreen(
    padding: PaddingValues,
    state: AppUiState,
    onOpenSession: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionTitle("План", "Три основных дня и короткие домашние практики")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.goals.forEach { goal ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (goal.type == GoalType.RUNNING) Icons.AutoMirrored.Filled.DirectionsRun else Icons.Default.Hiking,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(goal.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (goal.type == GoalType.RUNNING) "Весна 2027" else "Круглый год",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        item {
            Text("Стартовый восстановительный блок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(state.sessions.size, key = { state.sessions[it].id }) { index ->
            val session = state.sessions[index]
            Card(
                onClick = { onOpenSession(session.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (session.status == SessionStatus.COMPLETED) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else MaterialTheme.colorScheme.surface
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        when {
                            session.status == SessionStatus.COMPLETED -> Icons.Default.CheckCircle
                            session.type == "RUN" -> Icons.AutoMirrored.Filled.DirectionsRun
                            else -> Icons.Default.FitnessCenter
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${session.durationMinutes} мин", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(formatEpochDay(session.plannedEpochDay), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(session.objective, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (session.status) {
                                SessionStatus.COMPLETED -> "Выполнено · RPE ${session.actualRpe ?: "—"}"
                                SessionStatus.SKIPPED -> "Пропущено"
                                else -> "План · RPE ${session.targetRpe}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
