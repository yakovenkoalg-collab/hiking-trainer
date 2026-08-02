package ru.yakovenko.mountainform.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.yakovenko.mountainform.MountainFormApplication
import java.util.concurrent.TimeUnit

class YandexSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MountainFormApplication
        val settings = app.repository.currentSettings()
        if (!settings.automaticSync || !settings.yandexSyncEnabled || !app.secureTokenStore.hasToken()) {
            return Result.success()
        }
        return runCatching {
            val result = app.yandexDiskSyncManager.sync(settings.yandexRootPath)
            app.repository.updateSettings(
                settings.copy(
                    lastSyncAtEpochMillis = System.currentTimeMillis(),
                    lastSyncMessage = result.message,
                ),
            )
            Result.success()
        }.getOrElse { error ->
            app.repository.updateSettings(settings.copy(lastSyncMessage = error.message ?: "Ожидается сеть для Яндекс Диска"))
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "yandex-disk-auto-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<YandexSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
