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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.update.UpdateState

@Composable
fun MoreScreen(
    padding: PaddingValues,
    state: AppUiState,
    healthSummary: HealthSummary,
    updateState: UpdateState,
    onOpenProfile: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenPosture: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SectionTitle("Ещё", "Настройки и обслуживание отдельно от тренировки") }
        item {
            SettingsEntry(
                Icons.Default.Person,
                "Профиль, цели и ограничения",
                state.profile?.let { "${it.age} лет · ${it.preferredDays}" } ?: "Загрузка…",
                onOpenProfile,
            )
        }
        item {
            SettingsEntry(
                Icons.Default.HealthAndSafety,
                "Garmin и Health Connect",
                when {
                    healthSummary.permissionsGranted && healthSummary.hasAnyData -> "Данные получены"
                    healthSummary.permissionsGranted -> "Доступ есть, записей пока нет"
                    else -> "Требуется подключение"
                },
                onOpenHealth,
            )
        }
        item {
            SettingsEntry(
                Icons.Default.CloudSync,
                "Общая папка и резервные копии",
                state.settings?.sharedFolderName ?: "Папка не выбрана",
                onOpenSync,
            )
        }
        item {
            SettingsEntry(
                Icons.Default.AccessibilityNew,
                "Осанка",
                state.postureAssessments.firstOrNull()?.let { "Последняя оценка: ${it.selfRating}/5" }
                    ?: "Оценка ещё не выполнена",
                onOpenPosture,
            )
        }
        item {
            SettingsEntry(
                Icons.Default.Notifications,
                "Напоминания",
                if (state.settings?.remindersEnabled == true) {
                    "%02d:%02d".format(state.settings.reminderHour, state.settings.reminderMinute)
                } else "Выключены",
                onOpenReminders,
            )
        }
        item {
            SettingsEntry(
                Icons.Default.Info,
                "О приложении и обновления",
                updateState.message.ifBlank { "Версия ${ru.yakovenko.mountainform.BuildConfig.VERSION_NAME}" },
                onOpenAbout,
            )
        }
        item {
            Text(
                "Приложение не ставит диагнозы и не заменяет врача или физического терапевта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}
