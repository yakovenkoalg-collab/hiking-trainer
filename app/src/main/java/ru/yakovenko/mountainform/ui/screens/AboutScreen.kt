package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Горная форма", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Версия ${ru.yakovenko.mountainform.BuildConfig.VERSION_NAME}")
                    Text(updateState.message)
                    when {
                        updateState.downloadedFile != null -> Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("Установить обновление")
                        }
                        updateState.release != null -> Button(
                            onClick = onDownloadUpdate,
                            enabled = !updateState.checking,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Скачать ${updateState.release.versionName}") }
                        else -> OutlinedButton(
                            onClick = onCheckForUpdate,
                            enabled = !updateState.checking,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Проверить обновления") }
                    }
                    Text("Установку APK всегда подтверждает системный установщик Android.", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onShowPrivacy) { Text("Политика использования данных") }
            Text(
                "План и изменения нагрузки являются предложениями и применяются только после вашего подтверждения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
