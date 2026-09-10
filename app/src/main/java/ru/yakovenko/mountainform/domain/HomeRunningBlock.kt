package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/** A dated proposal, never a generator that advances the plan on each check. */
object HomeRunningBlock {
    const val PLAN_ID = "home-running-2026-09-10-v1"
    val availableFrom: LocalDate = LocalDate.of(2026, 9, 10)
    val through: LocalDate = LocalDate.of(2026, 9, 20)

    fun envelope(today: LocalDate, generatedAtEpochMillis: Long = System.currentTimeMillis()) = PlanEnvelope(
        planId = PLAN_ID,
        author = "Совместный план · дом и бег",
        reason = "10–20 сентября · версия 1. Домашняя силовая и три беговых дня на следующей неделе. " +
            "Лёгкий бег — разговорный темп, RPE 2–3; ускорения только 15 сентября, без спринта. " +
            "Утренние подтягивания и планка входят в общий объём, а не добавляются к силовой. " +
            "При боли или ухудшении восстановления не увеличивайте нагрузку. " +
            "Текущие ограничения приложения сохраняются; прошлое не меняется.",
        generatedAtEpochMillis = generatedAtEpochMillis,
        replacePlannedFromEpochDay = maxOf(today, availableFrom).toEpochDay(),
        replacePlannedThroughEpochDay = through.toEpochDay(),
        sessions = sessions().filter { it.plannedEpochDay >= today.toEpochDay() },
    )

    fun sessions(): List<PlanSession> = listOf(
        run(10, 20, "Опциональный лёгкий бег", "Можно пропустить после предыдущей пробежки: не догоняйте объём."),
        strength(11),
        run(13, 55, "Длительный лёгкий бег", "Без ускорения на финише. При усталости сократите до 45 минут."),
        stridesAndStrength(),
        run(17, 30, "Лёгкий бег", "25–30 минут, разговор полными фразами."),
        strength(18),
        run(20, 60, "Длительный лёгкий бег", "55–60 минут, только при нормальном восстановлении. Без быстрого финиша."),
    )

    private fun session(day: Int, title: String, type: String, minutes: Int, rpe: Int, objective: String, steps: List<ExerciseStep>) =
        PlanSession("home-running-${LocalDate.of(2026, 9, day).toEpochDay()}", LocalDate.of(2026, 9, day).toEpochDay(),
            title, type, "Беговая база · дома", objective, minutes, rpe, steps)

    private fun timed(id: String, title: String, seconds: Int, instructions: String) = ExerciseStep(
        id, title, "${seconds / 60} мин · RPE 2–3", instructions,
        exerciseId = if (id.contains("run")) "run-walk" else "walk",
        blockId = id, blockTitle = title, blockType = WorkoutBlockType.AEROBIC, workSeconds = seconds,
    )

    private fun run(day: Int, minutes: Int, title: String, note: String) = session(
        day, title, "RUN", minutes + 10, 3, note,
        listOf(timed("warmup", "Ходьба и разминка", 300, "Начните спокойно."),
            timed("easy-run", "Лёгкий бег", minutes * 60, "$note Ориентир — усилие, не обязательная скорость или пульс."),
            timed("cooldown", "Ходьба", 300, "Спокойно восстановите дыхание.")),
    )

    private fun strength(day: Int) = session(day, "Домашняя сила: ноги, верх и core", "STRENGTH", 65, 6,
        "Не до отказа: оставляйте 3–4 повтора в запасе. Без ухудшения плеча во время занятия и следующим утром.",
        listOf(
            timed("warmup", "Ходьба и разминка суставов", 480, "Безболезненная подвижность, затем пробные движения без веса."),
            lift("hinge", "Тяга гири с пола", "3 × 10 · 16 кг", "Таз назад, гиря близко. Прекратите при боли в плече.", 3, 10, 90, true),
            lift("split-squat", "Сплит-присед без веса", "3 × 8 на ногу", "Устойчивая стойка. Три секунды вниз, без боли в колене.", 3, 8, 75),
            lift("pull-up", "Подтягивания", "2 × 4 · вместо утренних", "Без рывков и отказа. Если уже подтягивались утром — пропустите этот блок. Только без боли.", 2, 4, 120, true),
            lift("step-down", "Контролируемый спуск со ступени", "2 × 8 на ногу", "Низкая устойчивая ступень, медленно вниз; без боли сзади колена.", 2, 8, 60),
            lift("calf", "Подъём на носок одной ногой", "2 × 12 на ногу", "Без раскачивания, плавное опускание.", 2, 12, 45),
            lift("bridge", "Ягодичный мост на одной ноге", "2 × 10 на ногу", "Сохраняйте таз ровным.", 2, 10, 45),
            ExerciseStep("forearm-plank", "Планка на предплечьях", "2 × 40 сек · вместо утренней",
                "Если уже сделали утренние 2 минуты — пропустите. Без боли, не задерживайте дыхание.",
                restrictionTags = listOf("SHOULDER_CLEARANCE_REQUIRED"), blockId = "plank", blockTitle = "Core",
                sets = 2, workSeconds = 40, restSeconds = 45),
        ),
    )

    private fun stridesAndStrength() = session(15, "Лёгкий бег + ускорения + короткая сила", "HYBRID", 60, 4,
        "40 минут бега вместе с ускорениями и восстановлением; затем короткий домашний блок.",
        listOf(
            timed("warmup", "Разминка ходьбой", 300, "Далее начните бег спокойно."),
            timed("easy-run", "Лёгкий бег", 1200, "20 минут RPE 2–3. Ускорения пропустите при усталости или боли."),
            ExerciseStep("stride", "Плавное ускорение", "4 × 15 сек · не спринт", "Плавно набирайте скорость на ровной поверхности, сохраняйте расслабленную технику.",
                blockId = "strides", blockTitle = "Ускорение / лёгкий бег", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 4, workSeconds = 15, restSeconds = 0),
            ExerciseStep("stride-recovery", "Очень лёгкий бег или шаг", "90 сек после каждого ускорения", "Полностью восстановите дыхание.",
                blockId = "strides", blockTitle = "Ускорение / лёгкий бег", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 4, workSeconds = 90, restAfterRoundSeconds = 0),
            timed("easy-run-finish", "Лёгкий бег после ускорений", 780, "13 минут спокойно. Если пропустили ускорения, просто бегите легко 35–40 минут суммарно."),
            timed("cooldown", "Ходьба", 300, "Восстановите дыхание перед домашним блоком."),
            lift("bridge", "Ягодичный мост", "2 × 12", "Пауза вверху, без опоры усилием на плечи.", 2, 12, 45),
            lift("core", "Антиразгибание лёжа", "2 × 8 на сторону", "Таз и рёбра неподвижны, двигайте ногами.", 2, 8, 45),
        ),
    )

    private fun lift(id: String, title: String, dose: String, note: String, sets: Int, reps: Int, rest: Int, clearance: Boolean = false) =
        ExerciseStep(id, title, dose, note,
            restrictionTags = if (clearance) listOf("SHOULDER_CLEARANCE_REQUIRED") else emptyList(),
            exerciseId = id, blockId = id, blockTitle = title, sets = sets, reps = reps, restSeconds = rest)
}
