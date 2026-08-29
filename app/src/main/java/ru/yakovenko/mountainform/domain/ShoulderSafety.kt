package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.ShoulderLoadPhase

enum class ShoulderExerciseAccess {
    ALLOWED,
    REQUIRES_CLEARANCE,
    BLOCKED,
}

object ShoulderSafety {
    private val fullReturnTags = setOf(
        "SHOULDER_ABDUCTION",
        "SHOULDER_LOADING",
        "OVERHEAD",
        "HANGING",
        "DIPS",
        "PUSH_UP",
        "PLANK",
        "CARRY",
    )

    private const val CLEARANCE_TAG = "SHOULDER_CLEARANCE_REQUIRED"
    private const val RETURNING_TAG = "SHOULDER_RETURN_TO_STRENGTH"

    private val riskWords = setOf(
        "над головой",
        "подтяг",
        "брусь",
        "отведен",
        "разведен",
        "жим вверх",
        "румынская тяга",
        "гир",
        "гантел",
        "планка",
        "отжим",
        "переноска",
        "overhead",
        "pull-up",
        "dips",
    )

    fun access(step: ExerciseStep, loadPhase: String = ShoulderLoadPhase.RESTRICTED): ShoulderExerciseAccess {
        val phaseIndex = ShoulderLoadPhase.ordered.indexOf(loadPhase).coerceAtLeast(0)
        val clearanceIndex = ShoulderLoadPhase.ordered.indexOf(ShoulderLoadPhase.THERAPIST_CLEARED)
        val returningIndex = ShoulderLoadPhase.ordered.indexOf(ShoulderLoadPhase.RETURNING)
        val fullIndex = ShoulderLoadPhase.ordered.indexOf(ShoulderLoadPhase.FULL)
        if (step.restrictionTags.any { it in fullReturnTags } && phaseIndex < fullIndex) {
            return ShoulderExerciseAccess.BLOCKED
        }
        if (RETURNING_TAG in step.restrictionTags && phaseIndex < returningIndex) {
            return ShoulderExerciseAccess.REQUIRES_CLEARANCE
        }
        if (CLEARANCE_TAG in step.restrictionTags && phaseIndex < clearanceIndex) {
            return ShoulderExerciseAccess.REQUIRES_CLEARANCE
        }
        val description = "${step.title} ${step.instructions}".lowercase()
        val explicitlyPhased = CLEARANCE_TAG in step.restrictionTags || RETURNING_TAG in step.restrictionTags
        if (riskWords.any(description::contains) && phaseIndex < fullIndex && !explicitlyPhased) {
            return ShoulderExerciseAccess.BLOCKED
        }
        return ShoulderExerciseAccess.ALLOWED
    }

    fun conflicts(step: ExerciseStep, loadPhase: String = ShoulderLoadPhase.RESTRICTED): Boolean =
        access(step, loadPhase) != ShoulderExerciseAccess.ALLOWED
}
