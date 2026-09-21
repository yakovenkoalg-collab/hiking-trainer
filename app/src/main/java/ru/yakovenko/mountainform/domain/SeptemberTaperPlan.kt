package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/** Explicitly agreed dated proposal; never applied by an APK update. */
object SeptemberTaperPlan {
    const val PLAN_ID = "september-taper-2026-09-22-v1"
    val availableFrom: LocalDate = LocalDate.of(2026, 9, 21)
    val through: LocalDate = LocalDate.of(2026, 9, 27)

    fun envelope(today: LocalDate, generatedAtEpochMillis: Long = System.currentTimeMillis()) = PlanEnvelope(
        planId = PLAN_ID,
        author = "Совместный план · бег",
        reason = "Согласованный план 22–27 сентября: бег во вторник, пятницу и воскресенье. " +
            "Снижение нагрузки перед стартом 4 октября; без дополнительной силовой для ног. " +
            "Ускорения и восстановление входят в длительность бега. Ходьба указана отдельно. " +
            "При боли или сохраняющейся усталости не догоняйте объём. Прошлые занятия и ограничения сохраняются.",
        generatedAtEpochMillis = generatedAtEpochMillis,
        replacePlannedFromEpochDay = maxOf(today, availableFrom).toEpochDay(),
        replacePlannedThroughEpochDay = through.toEpochDay(),
        sessions = sessions().filter { it.plannedEpochDay >= today.toEpochDay() },
    )

    fun sessions(): List<PlanSession> = listOf(
        run(22, "Очень лёгкий бег", 25,
            "20–25 минут, без ускорений. Только если ноги восстановились; при тяжести — прогулка вместо бега.",
            listOf(timed("easy-run", "Очень лёгкий бег", 25 * 60,
                "20–25 минут, RPE 2–3, разговор полными фразами. Можно закончить на 20-й минуте."))),
        run(25, "Лёгкий бег · опциональные ускорения", 35,
            "30–35 минут ВСЕГО: ускорения и 90 секунд восстановления уже включены. Только при свежих ногах.",
            listOf(
                timed("easy-run", "Лёгкий бег", 15 * 60, "15 минут спокойно. При усталости пропустите ускорения."),
                ExerciseStep("stride", "Плавное ускорение", "4 × 15 сек · опционально",
                    "Только при свежих ногах и без боли. Не спринт. При усталости пропустите весь блок ускорений.",
                    required = false, blockId = "strides", blockTitle = "Ускорение / восстановление",
                    blockType = WorkoutBlockType.CIRCUIT, rounds = 4, workSeconds = 15),
                ExerciseStep("stride-recovery", "Очень лёгкий бег", "90 сек после каждого ускорения",
                    "Восстановите дыхание. Если ускорения пропущены, пропустите и этот блок.",
                    required = false, blockId = "strides", blockTitle = "Ускорение / восстановление",
                    blockType = WorkoutBlockType.CIRCUIT, rounds = 4, workSeconds = 90),
                timed("easy-run-finish", "Спокойное завершение бега", 13 * 60,
                    "8–13 минут после ускорений: суммарно 30–35 минут бега. Без ускорений — 15–20 минут, чтобы суммарно было 30–35."),
            )),
        run(27, "Спокойный бег без быстрого финиша", 45,
            "40–45 минут легко. Без быстрого финиша и добавления километров.",
            listOf(timed("easy-run", "Лёгкий бег", 45 * 60,
                "40–45 минут разговорным темпом, RPE 2–3. Можно закончить на 40-й минуте. Не ускоряйтесь на финише."))),
    )

    private fun timed(id: String, title: String, seconds: Int, note: String) = ExerciseStep(
        id, title, "${seconds / 60} мин · RPE 2–3", note,
        exerciseId = if (id.contains("run")) "run-walk" else "walk",
        blockId = id, blockTitle = title, blockType = WorkoutBlockType.AEROBIC, workSeconds = seconds,
    )

    private fun run(day: Int, title: String, minutes: Int, note: String, running: List<ExerciseStep>): PlanSession {
        val epochDay = LocalDate.of(2026, 9, day).toEpochDay()
        return PlanSession("september-taper-$epochDay", epochDay, title, "RUN", "Подведение к старту",
            "$note Дополнительно 5 минут ходьбы до и 5 минут после бега.", minutes + 10, 3,
            listOf(timed("warmup", "Разминка ходьбой", 300, "Начните спокойно.")) + running +
                timed("cooldown", "Ходьба после бега", 300, "Спокойно восстановите дыхание."))
    }
}
