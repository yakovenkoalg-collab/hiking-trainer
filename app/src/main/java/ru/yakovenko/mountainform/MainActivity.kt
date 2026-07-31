package ru.yakovenko.mountainform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import ru.yakovenko.mountainform.health.HealthConnectManager
import ru.yakovenko.mountainform.ui.AppViewModel
import ru.yakovenko.mountainform.ui.MountainFormApp
import ru.yakovenko.mountainform.ui.theme.MountainFormTheme
import ru.yakovenko.mountainform.update.AppUpdateManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as MountainFormApplication
        val healthConnectManager = HealthConnectManager(this)
        val viewModel = ViewModelProvider(
            this,
            AppViewModel.Factory(application.repository, healthConnectManager, AppUpdateManager(applicationContext)),
        )[AppViewModel::class.java]
        val permissionLauncher = registerForActivityResult(healthConnectManager.permissionContract) {
            viewModel.refreshHealth()
        }

        setContent {
            MountainFormTheme {
                MountainFormApp(
                    viewModel = viewModel,
                    onRequestHealthPermissions = { permissionLauncher.launch(healthConnectManager.permissions) },
                    initialPrivacyPolicy = intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
                )
            }
        }
    }
}
