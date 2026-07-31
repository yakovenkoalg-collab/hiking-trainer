package ru.yakovenko.mountainform.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yakovenko.mountainform.data.ReadinessCheckEntity

class TrainingSafetyTest {
    @Test
    fun noCheckRequiresAssessment() {
        assertEquals(ReadinessLevel.YELLOW, TrainingSafety.evaluate(null).level)
    }

    @Test
    fun illnessStopsProgression() {
        assertEquals(ReadinessLevel.RED, TrainingSafety.evaluate(check(illness = true)).level)
    }

    @Test
    fun activeShoulderPainAdaptsSession() {
        assertEquals(ReadinessLevel.YELLOW, TrainingSafety.evaluate(check(shoulderPain = 4)).level)
    }

    @Test
    fun recoveredStateAllowsPlanWithoutExtraLoad() {
        assertEquals(ReadinessLevel.GREEN, TrainingSafety.evaluate(check()).level)
    }

    private fun check(
        sleep: Int = 4,
        energy: Int = 4,
        fatigue: Int = 2,
        shoulderPain: Int = 0,
        kneePain: Int = 0,
        illness: Boolean = false,
    ) = ReadinessCheckEntity(
        epochDay = 1,
        sleep = sleep,
        energy = energy,
        fatigue = fatigue,
        soreness = 2,
        shoulderPain = shoulderPain,
        kneePain = kneePain,
        illness = illness,
        notes = "",
        createdAtEpochMillis = 1,
    )
}
