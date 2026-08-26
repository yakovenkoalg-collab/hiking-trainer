package ru.yakovenko.mountainform.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import ru.yakovenko.mountainform.data.AppSettingsEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DataSyncScreen(
    settings: AppSettingsEntity?,
    onBack: () -> Unit,
    onSelectFolder: (Uri) -> Unit,
    onUpdateSettings: (AppSettingsEntity) -> Unit,
    onSync: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (Uri) -> Unit,
    yandexConnected: Boolean,
    yandexLoginConfigured: Boolean,
    onConnectYandex: (String) -> Unit,
    onDisconnectYandex: () -> Unit,
    onSyncYandex: () -> Unit,
    onCreateYandexBackup: () -> Unit,
    onShareReport: () -> Unit,
) {
    val context = LocalContext.current
    val current = settings ?: AppSettingsEntity()
    var showAdvanced by remember { mutableStateOf(false) }
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    val hasSyncStatus = current.lastSyncAtEpochMillis != null ||
        current.lastSyncMessage.isNotBlank() && current.lastSyncMessage != "Общая папка не выбрана"
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onSelectFolder(uri)
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestoreBackup(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обмен данными") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Яндекс Диск", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (yandexConnected) {
                        Text("Подключено · ${current.yandexAccountLabel.ifBlank { "Яндекс ID" }}", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = onSyncYandex, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Text("  Синхронизировать")
                        }
                        TextButton(onClick = { showDisconnectConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Отключить") }
                    } else {
                        Text(
                            "Для обмена без USB войдите с Яндекс ID.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { onConnectYandex("app:/") },
                            enabled = yandexLoginConfigured,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Войти с Яндекс ID") }
                        if (!yandexLoginConfigured) {
                            Text("В этой сборке не настроен вход Яндекс ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Автообмен", fontWeight = FontWeight.Bold)
                            Text("Обновлять после записей", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = current.automaticSync,
                            enabled = current.sharedFolderUri != null || yandexConnected,
                            onCheckedChange = { onUpdateSettings(current.copy(automaticSync = it)) },
                        )
                    }
                    if (current.sharedFolderUri == null && !yandexConnected) {
                        Text(
                            "Сначала войдите в Яндекс ID или выберите общую папку.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (hasSyncStatus) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (current.lastSyncAtEpochMillis == null) "Состояние обмена" else "Последний обмен",
                            fontWeight = FontWeight.Bold,
                        )
                        if (current.lastSyncMessage.isNotBlank()) {
                            Text(current.lastSyncMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        current.lastSyncAtEpochMillis?.let {
                            Text(formatSyncTime(it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("  Поделиться отчётом")
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAdvanced) "Скрыть дополнительные действия" else "Копии и общая папка")
            }

            if (showAdvanced) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Общая папка", fontWeight = FontWeight.Bold)
                        Text(current.sharedFolderName ?: "Папка не выбрана", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { folderPicker.launch(current.sharedFolderUri?.let(Uri::parse)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (current.sharedFolderUri == null) "Выбрать папку" else "Сменить папку")
                        }
                        if (current.sharedFolderUri != null) {
                            OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Text("  Синхронизировать папку")
                            }
                        }
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Резервные копии", fontWeight = FontWeight.Bold)
                        if (yandexConnected) {
                            OutlinedButton(onClick = onCreateYandexBackup, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Text("  Копия на Яндекс Диске")
                            }
                        }
                        OutlinedButton(onClick = onCreateBackup, enabled = current.sharedFolderUri != null, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Text("  Копия в общую папку")
                        }
                        OutlinedButton(onClick = { backupPicker.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Восстановить из файла")
                        }
                        Text(
                            "Отчёты содержат данные о самочувствии. Фото осанки и токен Яндекса не выгружаются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            title = { Text("Отключить Яндекс Диск?") },
            text = {
                Text(
                    "Автообмен через Яндекс остановится. Локальные данные и уже созданные файлы на Диске не удаляются.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmation = false
                        onDisconnectYandex()
                    },
                ) { Text("Отключить") }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirmation = false }) { Text("Отмена") } },
        )
    }
}

private fun formatSyncTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
