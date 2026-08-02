package ru.yakovenko.mountainform

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import ru.yakovenko.mountainform.health.HealthConnectManager
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
            ),
        )[AppViewModel::class.java]
        val permissionLauncher = registerForActivityResult(healthConnectManager.permissionContract) {
            viewModel.refreshHealth()
        }
        val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
                    initialPrivacyPolicy = intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
                )
            }
        }
    }
}
