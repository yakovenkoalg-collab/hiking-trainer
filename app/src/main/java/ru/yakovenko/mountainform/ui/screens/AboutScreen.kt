package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.update.UpdateOperation
import ru.yakovenko.mountainform.update.UpdateState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AboutScreen(
    updateState: UpdateState,
    onBack: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onShowPrivacy: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О приложении") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Горная форма", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Установлена версия ${ru.yakovenko.mountainform.BuildConfig.VERSION_NAME}")
                    if (updateState.message.isNotBlank()) Text(updateState.message)
                    when (updateState.operation) {
                        UpdateOperation.DOWNLOADING -> {
                            updateState.downloadProgress?.let { progress ->
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                transferSizeText(updateState.downloadedBytes, updateState.totalBytes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        UpdateOperation.CHECKING,
                        UpdateOperation.VERIFYING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        UpdateOperation.IDLE -> Unit
                    }
                    when {
                        updateState.downloadedFile != null -> Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("Установить обновление")
                        }
                        updateState.release != null -> Button(
                            onClick = onDownloadUpdate,
                            enabled = !updateState.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Скачать ${updateState.release.versionName}") }
                        else -> OutlinedButton(
                            onClick = onCheckForUpdate,
                            enabled = !updateState.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Проверить обновления") }
                    }
                    Text("Установку APK всегда подтверждает системный установщик Android.", style = MaterialTheme.typography.bodySmall)
                }
            } }
            item { TextButton(onClick = onShowPrivacy) { Text("Политика использования данных") } }
            item { Text(
                "План и изменения нагрузки являются предложениями и применяются только после вашего подтверждения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) }
        }
    }
}

private fun transferSizeText(downloadedBytes: Long, totalBytes: Long?): String {
    val downloaded = "%.1f МБ".format(downloadedBytes / 1_048_576.0)
    val total = totalBytes?.let { "%.1f МБ".format(it / 1_048_576.0) }
    val percent = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes * 100 / it).coerceIn(0, 100) }
    return if (total == null || percent == null) downloaded else "$downloaded из $total · $percent%"
}
