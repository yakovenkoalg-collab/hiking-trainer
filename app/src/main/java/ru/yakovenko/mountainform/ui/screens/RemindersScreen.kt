package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.AppSettingsEntity

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RemindersScreen(
    settings: AppSettingsEntity?,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onSave: (AppSettingsEntity) -> Unit,
) {
    val current = settings ?: AppSettingsEntity()
    var enabled by remember(current) { mutableStateOf(current.remindersEnabled) }
    var hour by remember(current) { mutableStateOf(current.reminderHour) }
    var minute by remember(current) { mutableStateOf(current.reminderMinute) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Напоминания") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ежедневная проверка", fontWeight = FontWeight.Bold)
                    Text(
                        "Напоминание о самочувствии и ближайшей тренировке",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    if (it) onRequestPermission()
                })
            } }
            item { Button(onClick = { showTimePicker = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Время: %02d:%02d".format(hour, minute))
            } }
            item { Text(
                "Android может доставить периодическое уведомление немного позже выбранного времени для экономии батареи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) }
            item { Button(
                onClick = {
                    onSave(current.copy(remindersEnabled = enabled, reminderHour = hour, reminderMinute = minute))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") } }
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Время напоминания") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTimePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Отмена") } },
        )
    }
}
