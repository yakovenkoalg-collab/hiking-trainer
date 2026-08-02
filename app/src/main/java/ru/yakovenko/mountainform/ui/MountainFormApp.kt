package ru.yakovenko.mountainform.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.yakovenko.mountainform.ui.screens.AboutScreen
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

private data class TopDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    TopDestination("today", "Сегодня", Icons.Default.Home),
    TopDestination("calendar", "Календарь", Icons.Default.DateRange),
    TopDestination("progress", "Прогресс", Icons.AutoMirrored.Filled.ShowChart),
    TopDestination("more", "Ещё", Icons.Default.MoreHoriz),
)

private val detailRoutes = setOf(
    "profile-settings", "health-settings", "sync-settings", "posture", "reminders", "about",
)

@Composable
fun MountainFormApp(
    viewModel: AppViewModel,
    onRequestHealthPermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    initialPrivacyPolicy: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthSummary by viewModel.healthSummary.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val backupPreview by viewModel.backupPreview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route.orEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(initialPrivacyPolicy) }

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
                SessionScreen(
                    padding = padding,
                    session = session,
                    steps = session?.let(viewModel::steps).orEmpty(),
                    catalog = state.exerciseCatalog,
                    stepLogs = state.stepLogs,
                    shoulderRestrictionActive = state.profile?.shoulderRestrictionActive == true,
                    loadBlocked = state.readinessDecision.level == ru.yakovenko.mountainform.domain.ReadinessLevel.RED,
                    adaptationRequired = state.readinessDecision.level == ru.yakovenko.mountainform.domain.ReadinessLevel.YELLOW,
                    readinessRecommendation = state.readinessDecision.recommendation,
                    onStepCompleted = viewModel::setStepCompleted,
                    onBack = { navController.popBackStack() },
                    onComplete = { sessionId, rpe, notes ->
                        viewModel.completeSession(sessionId, rpe, notes)
                        navController.popBackStack()
                    },
                    onSkip = { sessionId, reason ->
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
                Text(buildString {
                    append("Автор: ${preview.plan.author}\n")
                    append("Причина: ${preview.plan.reason}\n\n")
                    append("Новых тренировок: ${preview.added}\n")
                    append("Обновляемых: ${preview.updated}\n")
                    if (preview.conflicts.isNotEmpty()) {
                        append("\nКонфликты:\n")
                        append(preview.conflicts.joinToString("\n• ", prefix = "• "))
                    }
                })
            },
            confirmButton = {
                TextButton(enabled = preview.conflicts.isEmpty(), onClick = viewModel::applyImport) { Text("Применить") }
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
