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
        val decision = TrainingSafety.evaluate(check(illness = true))

        assertEquals(ReadinessLevel.RED, decision.level)
        assertEquals(listOf("отмечены признаки болезни"), decision.reasons)
    }

    @Test
    fun redDecisionExplainsEveryBlockingPainValue() {
        val decision = TrainingSafety.evaluate(check(shoulderPain = 8, kneePain = 7))

        assertEquals(ReadinessLevel.RED, decision.level)
        assertEquals(
            listOf("боль в левом плече 8/10", "боль в правом колене 7/10"),
            decision.reasons,
        )
    }

    @Test
    fun activeShoulderPainAdaptsSession() {
        val decision = TrainingSafety.evaluate(check(shoulderPain = 4))

        assertEquals(ReadinessLevel.YELLOW, decision.level)
        assertEquals(listOf("боль в левом плече 4/10"), decision.reasons)
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
