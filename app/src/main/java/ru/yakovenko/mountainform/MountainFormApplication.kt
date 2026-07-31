package ru.yakovenko.mountainform

import android.app.Application
import ru.yakovenko.mountainform.data.MountainFormDatabase
import ru.yakovenko.mountainform.data.MountainFormRepository

class MountainFormApplication : Application() {
    private val database by lazy { MountainFormDatabase.create(this) }
    val repository by lazy { MountainFormRepository(database.dao()) }
}
