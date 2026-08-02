package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ReadinessCheckEntity

enum class ReadinessLevel { GREEN, YELLOW, RED }

data class ReadinessDecision(
    val level: ReadinessLevel,
    val title: String,
    val recommendation: String,
    val reasons: List<String> = emptyList(),
)

object TrainingSafety {
    fun evaluate(check: ReadinessCheckEntity?): ReadinessDecision {
        if (check == null) {
            return ReadinessDecision(
                ReadinessLevel.YELLOW,
                "Сначала отметьте состояние",
                "Короткая проверка нужна перед изменением нагрузки.",
                listOf("нет оценки состояния на сегодня"),
            )
        }
        val stopReasons = buildList {
            if (check.illness) add("отмечены признаки болезни")
            if (check.shoulderPain >= 7) add("боль в левом плече ${check.shoulderPain}/10")
            if (check.kneePain >= 7) add("боль в правом колене ${check.kneePain}/10")
        }
        if (stopReasons.isNotEmpty()) {
            return ReadinessDecision(
                ReadinessLevel.RED,
                "Тренировка заблокирована",
                "Не начинайте или остановите тренировку, исключите болезненные движения и оцените необходимость медицинской помощи.",
                stopReasons,
            )
        }
        val adaptationReasons = buildList {
            if (check.shoulderPain >= 3) add("боль в левом плече ${check.shoulderPain}/10")
            if (check.kneePain >= 3) add("боль в правом колене ${check.kneePain}/10")
            if (check.sleep <= 2) add("сон ${check.sleep}/5")
            if (check.energy <= 2) add("энергия ${check.energy}/5")
            if (check.fatigue >= 4) add("усталость ${check.fatigue}/5")
        }
        if (adaptationReasons.isNotEmpty()) {
            return ReadinessDecision(
                ReadinessLevel.YELLOW,
                "Нужна адаптация",
                "Сохраните лёгкую часть, уменьшите объём и исключите движения, усиливающие симптомы.",
                adaptationReasons,
            )
        }
        return ReadinessDecision(
            ReadinessLevel.GREEN,
            "Можно выполнять план",
            "Сохраняйте целевой RPE и не добавляйте нагрузку сверх плана.",
        )
    }
}
