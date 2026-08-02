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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Для обмена без USB используйте Яндекс Диск. Общая папка Android остаётся локальным запасным вариантом.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Общая папка (запасной вариант)", fontWeight = FontWeight.Bold)
                    Text(
                        current.sharedFolderName ?: "Папка не выбрана",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = { folderPicker.launch(current.sharedFolderUri?.let(Uri::parse)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (current.sharedFolderUri == null) "Выбрать папку" else "Сменить папку")
                    }
                    if (current.sharedFolderUri != null) {
                        OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Text("  Синхронизировать общую папку")
                        }
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Яндекс Диск", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (yandexConnected) {
                        Text("Подключено · ${current.yandexAccountLabel.ifBlank { "OAuth" }}", color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (current.yandexRootPath.startsWith("app:")) {
                                "Папка приложения на Яндекс Диске"
                            } else {
                                current.yandexRootPath
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onSyncYandex, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Text("  Синхронизировать")
                        }
                        OutlinedButton(onClick = onCreateYandexBackup, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Text("  Копия на Яндекс Диске")
                        }
                        TextButton(onClick = onDisconnectYandex, modifier = Modifier.fillMaxWidth()) { Text("Отключить") }
                    } else {
                        Text(
                            "Войдите с сохранённым аккаунтом Яндекса. Доступ к Диску появится только после вашего подтверждения.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Файлы будут храниться только в папке приложения «Горная форма» на Яндекс Диске.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { onConnectYandex("app:/") },
                            enabled = yandexLoginConfigured,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Войти с Яндекс ID") }
                        Text(
                            if (yandexLoginConfigured) {
                                "Токен не нужно копировать: после входа он сохраняется в защищённом хранилище Android."
                            } else {
                                "В этой сборке ещё не указан Client ID Android-приложения в Яндекс OAuth."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Автообмен", fontWeight = FontWeight.Bold)
                            Text("Обновлять отчёт после записей", style = MaterialTheme.typography.bodySmall)
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

            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Состояние обмена", fontWeight = FontWeight.Bold)
                    Text(current.lastSyncMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    current.lastSyncAtEpochMillis?.let {
                        Text("Последний обмен: ${formatSyncTime(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("  Поделиться отчётом для корректировки плана")
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Резервные копии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = onCreateBackup,
                        enabled = current.sharedFolderUri != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Text("  Копия в общую папку")
                    }
                    OutlinedButton(
                        onClick = { backupPicker.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Восстановить из JSON") }
                }
            }

            Text(
                "Системная папка остаётся запасным вариантом. Для обмена без USB используйте Яндекс Диск. " +
                    "Отчёты содержат данные о самочувствии; фото осанки и облачный токен не выгружаются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSyncTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
