package ru.yakovenko.mountainform

import android.app.Application
import ru.yakovenko.mountainform.data.MountainFormDatabase
import ru.yakovenko.mountainform.data.MountainFormRepository
import ru.yakovenko.mountainform.sync.SecureTokenStore
import ru.yakovenko.mountainform.sync.YandexDiskSyncManager

class MountainFormApplication : Application() {
    private val database by lazy { MountainFormDatabase.create(this) }
    val repository by lazy { MountainFormRepository(database.dao()) }
    val secureTokenStore by lazy { SecureTokenStore(this) }
    val yandexDiskSyncManager by lazy { YandexDiskSyncManager(repository, secureTokenStore) }
}
