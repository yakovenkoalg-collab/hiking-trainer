package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep

object ShoulderSafety {
    private val restrictionTags = setOf(
        "SHOULDER_ABDUCTION",
        "SHOULDER_LOADING",
        "OVERHEAD",
        "HANGING",
        "DIPS",
        "PUSH_UP",
        "PLANK",
        "CARRY",
    )

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

    fun conflicts(step: ExerciseStep): Boolean {
        if (step.restrictionTags.any { it in restrictionTags }) return true
        val description = "${step.title} ${step.instructions}".lowercase()
        return riskWords.any(description::contains)
    }
}
