package ru.yakovenko.mountainform

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import ru.yakovenko.mountainform.health.HealthConnectManager
import ru.yakovenko.mountainform.health.FitActivityImporter
import ru.yakovenko.mountainform.reminders.ReminderScheduler
import ru.yakovenko.mountainform.sync.SharedFolderSyncManager
import ru.yakovenko.mountainform.ui.AppViewModel
import ru.yakovenko.mountainform.ui.MountainFormApp
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import ru.yakovenko.mountainform.update.AppUpdateManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as MountainFormApplication
        val healthConnectManager = HealthConnectManager(this)
        val reminderScheduler = ReminderScheduler(applicationContext)
        val sharedFolderSyncManager = SharedFolderSyncManager(applicationContext, application.repository)
        val viewModel = ViewModelProvider(
            this,
            AppViewModel.Factory(
                application.repository,
                healthConnectManager,
                AppUpdateManager(applicationContext),
                reminderScheduler,
                sharedFolderSyncManager,
                application.yandexDiskSyncManager,
                application.secureTokenStore,
                FitActivityImporter(applicationContext),
                application.workoutExecutionStore,
            ),
        )[AppViewModel::class.java]
        val permissionLauncher = registerForActivityResult(healthConnectManager.permissionContract) {
            viewModel.refreshHealth()
        }
        val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val yandexAuthSdk = YandexAuthSdk.create(YandexAuthOptions(this))
        var pendingYandexRootPath = "app:/"
        val yandexAuthLauncher = registerForActivityResult(yandexAuthSdk.contract) { result ->
            when (result) {
                is YandexAuthResult.Success -> viewModel.connectYandex(result.token.value, pendingYandexRootPath)
                is YandexAuthResult.Failure -> viewModel.reportYandexLoginMessage(
                    result.exception.message ?: "Не удалось войти через Яндекс ID",
                )
                YandexAuthResult.Cancelled -> viewModel.reportYandexLoginMessage("Вход через Яндекс ID отменён")
            }
        }

        setContent {
            MountainFormTheme {
                MountainFormApp(
                    viewModel = viewModel,
                    onRequestHealthPermissions = { permissionLauncher.launch(healthConnectManager.permissions) },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    yandexLoginConfigured = BuildConfig.YANDEX_CLIENT_ID.isNotBlank(),
                    onRequestYandexLogin = { rootPath ->
                        if (BuildConfig.YANDEX_CLIENT_ID.isBlank()) {
                            viewModel.reportYandexLoginMessage("В этой сборке ещё не указан Client ID Яндекс OAuth")
                        } else {
                            pendingYandexRootPath = rootPath
                            yandexAuthLauncher.launch(YandexAuthLoginOptions())
                        }
                    },
                    initialPrivacyPolicy = intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
                )
            }
        }
    }
}
