package ru.yakovenko.mountainform.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.oneDecimal
import ru.yakovenko.mountainform.update.UpdateState
import java.time.LocalDate

@Composable
fun ProfileScreen(
    padding: PaddingValues,
    state: AppUiState,
    healthSummary: HealthSummary,
    onRequestHealthPermissions: () -> Unit,
    onRefreshHealth: () -> Unit,
    onSaveBodyMetric: (Double?, Double?, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
    onExport: ((String) -> Unit) -> Unit,
    onImport: (String) -> Unit,
    updateState: UpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    var showBodyDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExport
        if (uri != null && content != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
        }
        pendingExport = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (content != null) onImport(content)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle("Профиль") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("41 год · 183 см · 75 кг", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Средний уровень · зал, бег, домашнее оборудование")
                    Text("Основные дни: вторник, пятница, воскресенье")
                    Text("OnePlus 15 · Android 16 · Garmin Fenix 8", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.profile?.shoulderRestrictionActive == true) {
            item { SafetyBanner("Левое плечо остаётся активным ограничением. Приложение блокирует конфликтующие шаги импортируемого плана.") }
        }
        item { SectionTitle("Health Connect", "Garmin Connect → Health Connect → Горная форма") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                when {
                                    healthSummary.permissionsGranted -> "Доступ разрешён"
                                    healthSummary.available -> "Нужно разрешение"
                                    else -> "Проверка подключения"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            healthSummary.latestSleepHours?.let { Text("Последний сон: ${it.oneDecimal()} ч") }
                            healthSummary.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (!healthSummary.permissionsGranted) {
                        Button(onClick = onRequestHealthPermissions, modifier = Modifier.fillMaxWidth()) { Text("Подключить Garmin через Health Connect") }
                    } else {
                        OutlinedButton(onClick = onRefreshHealth, modifier = Modifier.fillMaxWidth()) { Text("Обновить данные") }
                    }
                    Text("Данные читаются только после вашего разрешения и остаются в приложении.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onShowPrivacyPolicy) { Text("Как используются данные") }
                }
            }
        }
        item { SectionTitle("Форма и питание") }
        item {
            Card(onClick = { showBodyDialog = true }) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Добавить сегодняшние показатели", fontWeight = FontWeight.Bold)
                    val latest = state.bodyMetrics.firstOrNull()
                    Text(
                        latest?.let {
                            "Последнее: ${it.weightKg?.let { value -> "${value.oneDecimal()} кг" } ?: "вес —"}, " +
                                "${it.waistCm?.let { value -> "талия ${value.oneDecimal()} см" } ?: "талия —"}"
                        } ?: "Вес, талия и пищевые ориентиры ещё не заполнены",
                    )
                }
            }
        }
        item { SectionTitle("Совместная корректировка") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onExport { content ->
                            pendingExport = content
                            exportLauncher.launch("mountain-report-${LocalDate.now()}.json")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Text(" Экспортировать отчёт")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Text(" Импортировать новый план")
                }
                Text("Перед применением приложение покажет изменения и конфликты с ограничениями.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionTitle("Обновления") }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Версия ${ru.yakovenko.mountainform.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                    Text(updateState.message, style = MaterialTheme.typography.bodyMedium)
                    when {
                        updateState.downloadedFile != null -> Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) { Text("Установить обновление") }
                        updateState.release != null -> Button(onClick = onDownloadUpdate, enabled = !updateState.checking, modifier = Modifier.fillMaxWidth()) { Text("Скачать ${updateState.release.versionName}") }
                        else -> OutlinedButton(onClick = onCheckForUpdate, enabled = !updateState.checking, modifier = Modifier.fillMaxWidth()) { Text("Проверить обновления") }
                    }
                    Text("Android всегда попросит подтвердить установку APK.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text(
                "Приложение не ставит диагнозы и не заменяет врача или физического терапевта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showBodyDialog) {
        BodyMetricDialog(
            currentWeight = state.bodyMetrics.firstOrNull()?.weightKg ?: state.profile?.weightKg,
            currentWaist = state.bodyMetrics.firstOrNull()?.waistCm,
            onDismiss = { showBodyDialog = false },
            onSave = { weight, waist, protein, produce, hydration, alcoholFree, notes ->
                onSaveBodyMetric(weight, waist, protein, produce, hydration, alcoholFree, notes)
                showBodyDialog = false
            },
        )
    }
}

@Composable
private fun BodyMetricDialog(
    currentWeight: Double?,
    currentWaist: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
) {
    var weight by remember { mutableStateOf(currentWeight?.toString().orEmpty()) }
    var waist by remember { mutableStateOf(currentWaist?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(false) }
    var produce by remember { mutableStateOf(false) }
    var hydration by remember { mutableStateOf(false) }
    var alcoholFree by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Форма и питание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = it.replace(',', '.') }, label = { Text("Вес, кг") })
                OutlinedTextField(value = waist, onValueChange = { waist = it.replace(',', '.') }, label = { Text("Талия, см") })
                CheckRow("Цель по белку выполнена", protein) { protein = it }
                CheckRow("Овощи и фрукты", produce) { produce = it }
                CheckRow("Достаточно воды", hydration) { hydration = it }
                CheckRow("Без алкоголя", alcoholFree) { alcoholFree = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Комментарий") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(weight.toDoubleOrNull(), waist.toDoubleOrNull(), protein, produce, hydration, alcoholFree, notes)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
