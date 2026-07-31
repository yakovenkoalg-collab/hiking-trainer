package ru.yakovenko.mountainform.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.yakovenko.mountainform.ui.screens.PlanScreen
import ru.yakovenko.mountainform.ui.screens.ProfileScreen
import ru.yakovenko.mountainform.ui.screens.ProgressScreen
import ru.yakovenko.mountainform.ui.screens.SessionScreen
import ru.yakovenko.mountainform.ui.screens.TodayScreen

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    TopDestination("today", "Сегодня", Icons.Default.Home),
    TopDestination("plan", "План", Icons.Default.DateRange),
    TopDestination("progress", "Прогресс", Icons.AutoMirrored.Filled.ShowChart),
    TopDestination("profile", "Профиль", Icons.Default.Person),
)

@Composable
fun MountainFormApp(
    viewModel: AppViewModel,
    onRequestHealthPermissions: () -> Unit,
    initialPrivacyPolicy: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthSummary by viewModel.healthSummary.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!currentRoute.startsWith("session/")) {
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
        NavHost(
            navController = navController,
            startDestination = "today",
        ) {
            composable("today") {
                TodayScreen(
                    padding = padding,
                    state = state,
                    onSaveReadiness = viewModel::saveReadiness,
                    onOpenSession = { navController.navigate("session/$it") },
                    onCompleteCorePractice = viewModel::completeCorePractice,
                )
            }
            composable("plan") {
                PlanScreen(
                    padding = padding,
                    state = state,
                    onOpenSession = { navController.navigate("session/$it") },
                )
            }
            composable("progress") {
                ProgressScreen(padding = padding, state = state, healthSummary = healthSummary)
            }
            composable("profile") {
                ProfileScreen(
                    padding = padding,
                    state = state,
                    healthSummary = healthSummary,
                    onRequestHealthPermissions = onRequestHealthPermissions,
                    onRefreshHealth = viewModel::refreshHealth,
                    onSaveBodyMetric = viewModel::saveBodyMetric,
                    onExport = viewModel::exportReport,
                    onImport = viewModel::previewImport,
                    updateState = updateState,
                    onCheckForUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onInstallUpdate = viewModel::installUpdate,
                    onShowPrivacyPolicy = { showPrivacyPolicy = true },
                )
            }
            composable("session/{sessionId}") { entry ->
                val id = entry.arguments?.getString("sessionId")
                val session = state.sessions.firstOrNull { it.id == id }
                SessionScreen(
                    padding = padding,
                    session = session,
                    steps = session?.let(viewModel::steps).orEmpty(),
                    shoulderRestrictionActive = state.profile?.shoulderRestrictionActive == true,
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
                Text(
                    buildString {
                        append("Автор: ${preview.plan.author}\n")
                        append("Причина: ${preview.plan.reason}\n\n")
                        append("Новых тренировок: ${preview.added}\n")
                        append("Обновляемых: ${preview.updated}\n")
                        if (preview.conflicts.isNotEmpty()) {
                            append("\nКонфликты:\n")
                            append(preview.conflicts.joinToString("\n• ", prefix = "• "))
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = preview.conflicts.isEmpty(),
                    onClick = viewModel::applyImport,
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImport) { Text("Отмена") }
            },
        )
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Использование данных здоровья") },
            text = {
                Text(
                    "Горная форма читает выбранные вами данные Health Connect: тренировки, пульс, сон, шаги, " +
                        "дистанцию, набор высоты и вес. Они используются только для личного плана и сводок, " +
                        "хранятся в локальной базе приложения и не отправляются автоматически. Доступ можно " +
                        "отозвать в настройках Health Connect. Экспорт отчёта выполняется только вручную.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) { Text("Понятно") }
            },
        )
    }
}
