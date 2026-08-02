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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.data.UserProfileEntity
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.components.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProfileSettingsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSaveProfile: (UserProfileEntity) -> Unit,
    onSaveGoal: (GoalEventEntity) -> Unit,
) {
    val profile = state.profile
    val runningGoal = state.goals.firstOrNull { it.type == GoalType.RUNNING }
    if (profile == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Профиль") }) }) { padding ->
            Text("Загрузка…", Modifier.padding(padding).padding(20.dp))
        }
        return
    }

    var age by remember(profile) { mutableStateOf(profile.age.toString()) }
    var height by remember(profile) { mutableStateOf(profile.heightCm.toString()) }
    var weight by remember(profile) { mutableStateOf(profile.weightKg.toString()) }
    var preferredDays by remember(profile) { mutableStateOf(profile.preferredDays) }
    var phase by remember(profile) { mutableStateOf(profile.currentPhase) }
    var shoulderActive by remember(profile) { mutableStateOf(profile.shoulderRestrictionActive) }
    var kneeActive by remember(profile) { mutableStateOf(profile.kneeObservationActive) }
    var distance by remember(runningGoal) { mutableStateOf(runningGoal?.distanceKm ?: 21.1) }
    var targetDate by remember(runningGoal) {
        mutableStateOf(runningGoal?.targetEpochDay?.let(LocalDate::ofEpochDay))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль и цели") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionTitle("Исходные данные") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Возраст") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(height, { height = it.filter(Char::isDigit) }, label = { Text("Рост, см") }, modifier = Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(
                    weight,
                    { weight = it.replace(',', '.').filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Вес, кг") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    preferredDays,
                    { preferredDays = it },
                    label = { Text("Основные дни тренировок") },
                    supportingText = { Text("Например: вторник, пятница, воскресенье") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    phase,
                    { phase = it },
                    label = { Text("Текущая фаза") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SectionTitle("Активные ограничения", "Отключайте ограничение только после собственной оценки или специалиста") }
            item { CheckSetting("Левое плечо", shoulderActive) { shoulderActive = it } }
            item { CheckSetting("Наблюдение за правым коленом", kneeActive) { kneeActive = it } }
            if (shoulderActive) {
                item { SafetyBanner("Болезненное отведение, движения над головой, подтягивания и брусья остаются заблокированы.") }
            }
            item { SectionTitle("Весенний беговой старт") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = distance == 21.1, onClick = { distance = 21.1 }, label = { Text("21,1 км") })
                    FilterChip(selected = distance == 42.2, onClick = { distance = 42.2 }, label = { Text("42,2 км") })
                }
            }
            item {
                Button(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(targetDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Выбрать дату старта")
                }
            }
            item {
                Button(
                    enabled = age.toIntOrNull() != null && height.toIntOrNull() != null && weight.toDoubleOrNull() != null,
                    onClick = {
                        onSaveProfile(
                            profile.copy(
                                age = age.toInt(),
                                heightCm = height.toInt(),
                                weightKg = weight.toDouble(),
                                preferredDays = preferredDays.trim(),
                                currentPhase = phase.trim(),
                                shoulderRestrictionActive = shoulderActive,
                                kneeObservationActive = kneeActive,
                            ),
                        )
                        runningGoal?.let {
                            onSaveGoal(
                                it.copy(
                                    title = "Весенний старт: ${distance.toString().replace('.', ',')} км",
                                    targetEpochDay = targetDate?.toEpochDay(),
                                    distanceKm = distance,
                                    status = if (distance == 42.2) "MARATHON_CANDIDATE" else "HALF_MARATHON_BASE",
                                ),
                            )
                        }
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить") }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = (targetDate ?: LocalDate.now().plusMonths(6)).toEpochDay() * 86_400_000L
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetDate = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun CheckSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column {
            Text(label, fontWeight = FontWeight.Bold)
            Text(
                if (checked) "Активно" else "Не активно",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
