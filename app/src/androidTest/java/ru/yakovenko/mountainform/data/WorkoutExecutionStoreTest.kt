package ru.yakovenko.mountainform.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yakovenko.mountainform.domain.WorkoutExecutionState
import ru.yakovenko.mountainform.domain.WorkoutTimerMode

class WorkoutExecutionStoreTest {
    @Test
    fun runningTimerCanBeSerializedAndRestoredOnAndroidRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutExecutionStore(context)
        val state = WorkoutExecutionState(
            sessionId = "android-serialization",
            workoutStarted = true,
            workoutElapsedSeconds = 42,
            timerMode = WorkoutTimerMode.SET,
            workRemainingSeconds = 58,
        )

        store.clear(state.sessionId)
        store.save(state)

        assertEquals(state, store.load(state.sessionId))
        store.clear(state.sessionId)
    }
}
