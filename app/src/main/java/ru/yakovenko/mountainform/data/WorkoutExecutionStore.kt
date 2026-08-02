package ru.yakovenko.mountainform.data

import android.content.Context
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.domain.WorkoutExecutionState

class WorkoutExecutionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(sessionId: String): WorkoutExecutionState? = preferences.getString(key(sessionId), null)?.let { raw ->
        runCatching { json.decodeFromString<WorkoutExecutionState>(raw) }.getOrNull()
    }

    fun save(state: WorkoutExecutionState) {
        preferences.edit().putString(key(state.sessionId), json.encodeToString(state)).apply()
    }

    fun clear(sessionId: String) {
        preferences.edit().remove(key(sessionId)).apply()
    }

    private fun key(sessionId: String) = "session_$sessionId"

    private companion object {
        const val PREFERENCES = "workout_execution"
    }
}
