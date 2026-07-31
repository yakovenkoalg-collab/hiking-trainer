package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ReadinessCheckEntity

enum class ReadinessLevel { GREEN, YELLOW, RED }

data class ReadinessDecision(
    val level: ReadinessLevel,
    val title: String,
    val recommendation: String,
)

object TrainingSafety {
    fun evaluate(check: ReadinessCheckEntity?): ReadinessDecision {
        if (check == null) {
            return ReadinessDecision(
                ReadinessLevel.YELLOW,
                "Сначала отметьте состояние",
                "Короткая проверка нужна перед изменением нагрузки.",
            )
        }
        if (check.illness || check.shoulderPain >= 7 || check.kneePain >= 7) {
            return ReadinessDecision(
                ReadinessLevel.RED,
                "Нагрузку не повышаем",
                "Отмените болезненные движения и оцените необходимость медицинской помощи.",
            )
        }
        if (
            check.shoulderPain >= 3 || check.kneePain >= 3 || check.sleep <= 2 ||
            check.energy <= 2 || check.fatigue >= 4
        ) {
            return ReadinessDecision(
                ReadinessLevel.YELLOW,
                "Нужна адаптация",
                "Сохраните лёгкую часть, уменьшите объём и исключите движения, усиливающие симптомы.",
            )
        }
        return ReadinessDecision(
            ReadinessLevel.GREEN,
            "Можно выполнять план",
            "Сохраняйте целевой RPE и не добавляйте нагрузку сверх плана.",
        )
    }
}
