package ru.yakovenko.mountainform.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.yakovenko.mountainform.ui.screens.AboutScreen
import ru.yakovenko.mountainform.ui.screens.ActivitiesScreen
import ru.yakovenko.mountainform.ui.screens.CalendarScreen
import ru.yakovenko.mountainform.ui.screens.DataSyncScreen
import ru.yakovenko.mountainform.ui.screens.HealthSettingsScreen
import ru.yakovenko.mountainform.ui.screens.MoreScreen
import ru.yakovenko.mountainform.ui.screens.PostureScreen
import ru.yakovenko.mountainform.ui.screens.ProfileSettingsScreen
import ru.yakovenko.mountainform.ui.screens.ProgressScreen
import ru.yakovenko.mountainform.ui.screens.RemindersScreen
import ru.yakovenko.mountainform.ui.screens.SessionScreen
import ru.yakovenko.mountainform.ui.screens.TodayScreen
import ru.yakovenko.mountainform.data.PlanSessionSummary

private data class TopDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    TopDestination("today", "Сегодня", Icons.Default.Home),
    TopDestination("calendar", "Календарь", Icons.Default.DateRange),
    TopDestination("progress", "Прогресс", Icons.AutoMirrored.Filled.ShowChart),
    TopDestination("more", "Ещё", Icons.Default.MoreHoriz),
)

private val detailRoutes = setOf(
    "profile-settings", "health-settings", "sync-settings", "posture", "reminders", "about", "activities",
)

@Composable
fun MountainFormApp(
    viewModel: AppViewModel,
    onRequestHealthPermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    yandexLoginConfigured: Boolean,
    onRequestYandexLogin: (String) -> Unit,
    initialPrivacyPolicy: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthSummary by viewModel.healthSummary.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val backupPreview by viewModel.backupPreview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val yandexConnected by viewModel.yandexConnected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val workoutSignal = remember(context) { WorkoutSignal(context.applicationContext) }
    DisposableEffect(workoutSignal) {
        onDispose { workoutSignal.release() }
    }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route.orEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(initialPrivacyPolicy) }
    var openReadinessOnToday by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val showBottomBar = !currentRoute.startsWith("session/") && currentRoute !in detailRoutes
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = "today") {
            composable("today") {
                TodayScreen(
                    padding = padding,
                    state = state,
                    onSaveReadiness = viewModel::saveReadiness,
                    onOpenSession = { navController.navigate("session/$it") },
                    onCompleteCorePractice = viewModel::completeCorePractice,
                    onShareReviewReport = {
                        viewModel.exportReport { report ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "Горная форма — контрольная точка")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
                        }
                    },
                    openReadiness = openReadinessOnToday,
                    onReadinessOpened = { openReadinessOnToday = false },
                )
            }
            composable("calendar") {
                CalendarScreen(
                    padding = padding,
                    state = state,
                    onOpenSession = { navController.navigate("session/$it") },
                    onReschedule = viewModel::rescheduleSession,
                    onProposeNextBlock = viewModel::proposeNextBaseBlock,
                )
            }
            composable("progress") {
                ProgressScreen(
                    padding = padding,
                    state = state,
                    healthSummary = healthSummary,
                    onSaveBodyMetric = viewModel::saveBodyMetric,
                    onOpenActivities = { navController.navigate("activities") },
                )
            }
            composable("more") {
                MoreScreen(
                    padding = padding,
                    state = state,
                    healthSummary = healthSummary,
                    updateState = updateState,
                    onOpenProfile = { navController.navigate("profile-settings") },
                    onOpenHealth = { navController.navigate("health-settings") },
                    onOpenSync = { navController.navigate("sync-settings") },
                    onOpenPosture = { navController.navigate("posture") },
                    onOpenReminders = { navController.navigate("reminders") },
                    onOpenAbout = { navController.navigate("about") },
                )
            }
            composable("profile-settings") {
                ProfileSettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSaveProfile = viewModel::updateProfile,
                    onSaveGoal = viewModel::updateGoal,
                )
            }
            composable("health-settings") {
                HealthSettingsScreen(
                    summary = healthSummary,
                    onBack = { navController.popBackStack() },
                    onRequestPermissions = onRequestHealthPermissions,
                    onRefresh = viewModel::refreshHealth,
                    onWindowChange = viewModel::setHealthWindow,
                    onShowPrivacy = { showPrivacyPolicy = true },
                    onImportFit = viewModel::importFit,
                )
            }
            composable("activities") {
                ActivitiesScreen(
                    activities = state.importedActivities,
                    sessions = state.sessions,
                    onBack = { navController.popBackStack() },
                    onLinkActivity = viewModel::linkActivity,
                    onIgnoreActivity = viewModel::ignoreActivity,
                )
            }
            composable("sync-settings") {
                DataSyncScreen(
                    settings = state.settings,
                    onBack = { navController.popBackStack() },
                    onSelectFolder = viewModel::selectSharedFolder,
                    onUpdateSettings = viewModel::updateSettings,
                    onSync = viewModel::syncSharedFolder,
                    onCreateBackup = viewModel::createSharedBackup,
                    onRestoreBackup = viewModel::previewBackup,
                    yandexConnected = yandexConnected,
                    yandexLoginConfigured = yandexLoginConfigured,
                    onConnectYandex = onRequestYandexLogin,
                    onDisconnectYandex = viewModel::disconnectYandex,
                    onSyncYandex = viewModel::syncYandex,
                    onCreateYandexBackup = viewModel::createYandexBackup,
                    onShareReport = {
                        viewModel.exportReport { report ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "Горная форма — отчёт для корректировки плана")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
                        }
                    },
                )
            }
            composable("posture") {
                PostureScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = viewModel::savePostureAssessment,
                )
            }
            composable("reminders") {
                RemindersScreen(
                    settings = state.settings,
                    onBack = { navController.popBackStack() },
                    onRequestPermission = onRequestNotificationPermission,
                    onSave = viewModel::updateSettings,
                )
            }
            composable("about") {
                AboutScreen(
                    updateState = updateState,
                    onBack = { navController.popBackStack() },
                    onCheckForUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onInstallUpdate = viewModel::installUpdate,
                    onShowPrivacy = { showPrivacyPolicy = true },
                )
            }
            composable("session/{sessionId}") { entry ->
                val id = entry.arguments?.getString("sessionId")
                val session = state.sessions.firstOrNull { it.id == id }
                val initialExecutionState = remember(id) { id?.let(viewModel::loadWorkoutExecution) }
                SessionScreen(
                    padding = padding,
                    session = session,
                    steps = session?.let(viewModel::steps).orEmpty(),
                    catalog = state.exerciseCatalog,
                    stepLogs = state.stepLogs,
                    setLogs = state.setLogs,
                    shoulderRestrictionActive = state.profile?.shoulderRestrictionActive == true,
                    loadBlocked = state.readinessDecision.level == ru.yakovenko.mountainform.domain.ReadinessLevel.RED,
                    adaptationRequired = state.readinessDecision.level == ru.yakovenko.mountainform.domain.ReadinessLevel.YELLOW,
                    readinessRecommendation = state.readinessDecision.recommendation,
                    readinessReasons = state.readinessDecision.reasons,
                    initialExecutionState = initialExecutionState,
                    onExecutionStateChanged = viewModel::saveWorkoutExecution,
                    onStepCompleted = viewModel::setStepCompleted,
                    onSaveSetLog = viewModel::saveSetLog,
                    onBack = { navController.popBackStack() },
                    onEditReadiness = {
                        openReadinessOnToday = true
                        navController.navigate("today") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onTimerFinished = workoutSignal::play,
                    onComplete = { sessionId, rpe, notes, actualDurationSeconds ->
                        viewModel.clearWorkoutExecution(sessionId)
                        viewModel.completeSession(sessionId, rpe, notes, actualDurationSeconds)
                        navController.popBackStack()
                    },
                    onSkip = { sessionId, reason ->
                        viewModel.clearWorkoutExecution(sessionId)
                        viewModel.skipSession(sessionId, reason)
                        navController.popBackStack()
                    },
                )
            }
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text("Предпросмотр нового плана") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Автор: ${preview.plan.author}", fontWeight = FontWeight.SemiBold)
                            Text(preview.plan.reason)
                            Text("Добавится: ${preview.added} · изменится: ${preview.updated}")
                            if (preview.preservedHistory > 0) {
                                Text("История сохранена без изменений: ${preview.preservedHistory}")
                            }
                        }
                    }
                    if (preview.conflicts.isNotEmpty()) {
                        item { Text("Конфликты — план нельзя применить", fontWeight = FontWeight.Bold) }
                        items(preview.conflicts.size) { index -> Text("• ${preview.conflicts[index]}") }
                    }
                    if (preview.changes.isEmpty()) {
                        item { Text("Применимых изменений нет: существующая история останется без изменений.") }
                    }
                    items(preview.changes.size) { index ->
                        val change = preview.changes[index]
                        Card {
                            Column(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    if (change.before == null) "Добавится тренировка" else "Изменится тренировка",
                                    fontWeight = FontWeight.Bold,
                                )
                                change.before?.let {
                                    Text("Было", fontWeight = FontWeight.SemiBold)
                                    PlanSummary(it)
                                }
                                Text(if (change.before == null) "План" else "Станет", fontWeight = FontWeight.SemiBold)
                                PlanSummary(change.after)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.conflicts.isEmpty() && preview.changes.isNotEmpty(),
                    onClick = viewModel::applyImport,
                ) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text("Отмена") } },
        )
    }

    backupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            title = { Text("Восстановить копию?") },
            text = {
                Text(
                    "Новых тренировок: ${preview.newSessions}\n" +
                        "Записей самочувствия: ${preview.readinessRecords}\n" +
                        "Метрик тела: ${preview.bodyMetricRecords}\n\n" +
                        "Ваши локальные выполненные и пропущенные тренировки не перезапишутся.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::applyBackup) { Text("Восстановить") } },
            dismissButton = { TextButton(onClick = viewModel::dismissBackup) { Text("Отмена") } },
        )
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Использование данных здоровья") },
            text = {
                Text(
                    "Горная форма читает выбранные вами данные Health Connect: тренировки, пульс, сон, шаги, " +
                        "дистанцию, набор высоты и вес. Они используются локально. В общую папку отчёт попадает только после вашего выбора.",
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyPolicy = false }) { Text("Понятно") } },
        )
    }
}

@Composable
private fun PlanSummary(summary: PlanSessionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("${formatEpochDay(summary.plannedEpochDay)} · ${summary.title}")
        Text("${summary.durationMinutes} мин · RPE ${summary.targetRpe}")
        summary.exercises.forEach { Text("• $it", style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
    }
}
