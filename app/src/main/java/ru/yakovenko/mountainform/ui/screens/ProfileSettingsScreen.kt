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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
    var restrictionToDisable by remember { mutableStateOf<String?>(null) }
    val parsedAge = age.toIntOrNull()
    val parsedHeight = height.toIntOrNull()
    val parsedWeight = weight.toDoubleOrNull()
    val ageInvalid = parsedAge == null || parsedAge !in 18..100
    val heightInvalid = parsedHeight == null || parsedHeight !in 120..230
    val weightInvalid = parsedWeight == null || parsedWeight !in 35.0..250.0
    val preferredDaysInvalid = preferredDays.isBlank()
    val phaseInvalid = phase.isBlank()
    val profileValid = !ageInvalid && !heightInvalid && !weightInvalid && !preferredDaysInvalid && !phaseInvalid

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
                    OutlinedTextField(
                        age,
                        { age = it.filter(Char::isDigit).take(3) },
                        label = { Text("Возраст") },
                        isError = ageInvalid,
                        supportingText = { if (ageInvalid) Text("18–100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        height,
                        { height = it.filter(Char::isDigit).take(3) },
                        label = { Text("Рост, см") },
                        isError = heightInvalid,
                        supportingText = { if (heightInvalid) Text("120–230") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                OutlinedTextField(
                    weight,
                    { weight = it.replace(',', '.').filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Вес, кг") },
                    isError = weightInvalid,
                    supportingText = { if (weightInvalid) Text("Укажите 35–250 кг") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    preferredDays,
                    { preferredDays = it },
                    label = { Text("Основные дни тренировок") },
                    supportingText = { Text("Например: вторник, пятница, воскресенье") },
                    isError = preferredDaysInvalid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    phase,
                    { phase = it },
                    label = { Text("Текущая фаза") },
                    isError = phaseInvalid,
                    supportingText = { if (phaseInvalid) Text("Заполните текущую фазу") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SectionTitle("Активные ограничения", "Отключайте ограничение только после собственной оценки или специалиста") }
            item {
                CheckSetting("Левое плечо", shoulderActive) { next ->
                    if (!next && shoulderActive) restrictionToDisable = "shoulder" else shoulderActive = next
                }
            }
            item {
                CheckSetting("Наблюдение за правым коленом", kneeActive) { next ->
                    if (!next && kneeActive) restrictionToDisable = "knee" else kneeActive = next
                }
            }
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
                    enabled = profileValid,
                    onClick = {
                        onSaveProfile(
                            profile.copy(
                                age = requireNotNull(parsedAge),
                                heightCm = requireNotNull(parsedHeight),
                                weightKg = requireNotNull(parsedWeight),
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

    restrictionToDisable?.let { restriction ->
        val shoulder = restriction == "shoulder"
        AlertDialog(
            onDismissRequest = { restrictionToDisable = null },
            title = { Text("Отключить ограничение?") },
            text = {
                Text(
                    if (shoulder) {
                        "Приложение перестанет блокировать нагрузку на плечо. Это не означает, что плечо восстановилось."
                    } else {
                        "Приложение перестанет напоминать о контроле колена после длинных спусков."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (shoulder) shoulderActive = false else kneeActive = false
                        restrictionToDisable = null
                    },
                ) { Text("Отключить") }
            },
            dismissButton = { TextButton(onClick = { restrictionToDisable = null }) { Text("Отмена") } },
        )
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
