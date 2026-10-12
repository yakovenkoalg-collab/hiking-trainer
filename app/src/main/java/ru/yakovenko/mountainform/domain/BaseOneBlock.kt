package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/** Explicitly agreed dated proposal; never applied by an APK update. */
object BaseOneBlock {
    const val PLAN_ID = "base-one-2026-10-13-v3"

    /** Earlier revisions of the same block (file export and the first bundled one); this revision supersedes them. */
    val SUPERSEDED_PLAN_IDS = setOf("base1-2026-10-13-v1", "base-one-2026-10-13-v2")
    const val EASY_HEART_RATE_MAX = 148
    val availableFrom: LocalDate = LocalDate.of(2026, 10, 12)
    val through: LocalDate = LocalDate.of(2026, 11, 15)

    private const val PHASE = "База 1 · лёгкий бег"
    private const val EASY_RUN_TECHNIQUE = "Разговор полными фразами. Пульс выше $EASY_HEART_RATE_MAX — замедлитесь " +
        "или идите шагом, пока не опустится до 140. На дорожке: уклон 1%, 8,3–9,0 км/ч по пульсу. " +
        "При боли, которая меняет технику, остановитесь."
    private const val EASY_RUN = "Пульс не выше $EASY_HEART_RATE_MAX, темп любой. Выше — шаг, пока не опустится до 140."
    private const val LOW_READINESS = "При низкой готовности утром — ходьба 30 минут."
    private const val LONG_RUN = "На подъёмах — шаг."
    private const val AFTER_STRENGTH = "После силовой выбирайте ровный маршрут."
    private const val DELOAD = "Разгрузка: объём не добавляйте."

    /**
     * [restrictedShoulderPhase] is the profile's load phase while the shoulder restriction is active, otherwise null.
     * Steps the restriction does not allow are left out, never replaced, so the running part stays applicable.
     */
    fun envelope(
        today: LocalDate,
        generatedAtEpochMillis: Long = System.currentTimeMillis(),
        restrictedShoulderPhase: String? = null,
    ) = PlanEnvelope(
        planId = PLAN_ID,
        author = "Совместный план · база 1",
        reason = (if (omitsSteps(restrictedShoulderPhase)) {
            "Упражнения, закрытые текущим ограничением плеча, в блок не включены. "
        } else "") + "13 октября — 15 ноября. Три лёгкие пробежки с границей пульса $EASY_HEART_RATE_MAX и одна домашняя " +
            "силовая в неделю. Бег растёт со 110 до 170 минут в неделю, 9–15 ноября — разгрузка. " +
            "Версия 3: тексты занятий сокращены, нагрузка и даты не менялись. " +
            "При боли, болезни или ухудшении на следующий день нагрузку сократить; пропущенное не компенсировать.",
        generatedAtEpochMillis = generatedAtEpochMillis,
        replacePlannedFromEpochDay = maxOf(today, availableFrom.plusDays(1)).toEpochDay(),
        replacePlannedThroughEpochDay = through.toEpochDay(),
        sessions = sessions(restrictedShoulderPhase).filter { it.plannedEpochDay >= today.toEpochDay() },
    )

    fun sessions(restrictedShoulderPhase: String?): List<PlanSession> =
        if (restrictedShoulderPhase == null) sessions() else sessions().map { session ->
            session.copy(steps = session.steps.filterNot { ShoulderSafety.conflicts(it, restrictedShoulderPhase) })
        }

    private fun omitsSteps(restrictedShoulderPhase: String?) =
        restrictedShoulderPhase != null && sessions(restrictedShoulderPhase) != sessions()

    fun sessions(): List<PlanSession> = listOf(
        run(10, 13, 30, "Лёгкий бег · 30 мин", LOW_READINESS),
        strength(10, 14, sets = 2, squatReps = 8, stepReps = 8, light = true),
        run(10, 16, 35, "Лёгкий бег · 35 мин", LOW_READINESS),
        run(10, 18, 45, "Длительный лёгкий бег · 45 мин", LONG_RUN),

        run(10, 20, 35, "Лёгкий бег · 35 мин", "Пропущенное не компенсируйте."),
        strength(10, 21, sets = 3, squatReps = 8, stepReps = 8),
        run(10, 23, 40, "Лёгкий бег · 40 мин", AFTER_STRENGTH),
        run(10, 25, 60, "Длительный лёгкий бег · 60 мин", LONG_RUN),

        stridesRun(10, 27, firstMinutes = 25, strides = 4, lastMinutes = 8),
        strength(10, 28, sets = 3, squatReps = 10, stepReps = 8),
        run(10, 30, 40, "Лёгкий бег · 40 мин", AFTER_STRENGTH),
        run(11, 1, 70, "Длительный лёгкий бег · 70 мин", "$LONG_RUN Возьмите воду."),

        stridesRun(11, 3, firstMinutes = 26, strides = 6, lastMinutes = 8),
        strength(11, 4, sets = 3, squatReps = 10, stepReps = 10),
        run(11, 6, 45, "Лёгкий бег · 45 мин", AFTER_STRENGTH),
        run(11, 8, 80, "Длительный лёгкий бег · 80 мин", "$LONG_RUN Возьмите воду."),

        run(11, 10, 35, "Лёгкий бег · 35 мин · разгрузка", DELOAD),
        strength(11, 11, sets = 2, squatReps = 8, stepReps = 8, light = true),
        run(11, 13, 35, "Лёгкий бег · 35 мин · разгрузка", DELOAD),
        run(11, 15, 60, "Длительный лёгкий бег · 60 мин · разгрузка", LONG_RUN),
    )

    private fun session(
        month: Int, day: Int, title: String, type: String, minutes: Int, rpe: Int, objective: String,
        steps: List<ExerciseStep>,
    ): PlanSession {
        val epochDay = LocalDate.of(2026, month, day).toEpochDay()
        return PlanSession("base1-$epochDay", epochDay, title, type, PHASE, objective, minutes, rpe, steps)
    }

    private fun walk(id: String, title: String, seconds: Int, instructions: String) = ExerciseStep(
        id, title, "${seconds / 60} минут", instructions, exerciseId = "walk", illustrationKey = "walk",
        blockId = id, blockTitle = title, blockType = WorkoutBlockType.AEROBIC, workSeconds = seconds,
    )

    private fun easy(id: String, title: String, minutes: Int, instructions: String) = ExerciseStep(
        id, title, "$minutes минут · пульс до $EASY_HEART_RATE_MAX", instructions,
        exerciseId = "run-walk", illustrationKey = "run-walk", blockId = id, blockTitle = title,
        blockType = WorkoutBlockType.AEROBIC, workSeconds = minutes * 60, targetHeartRateMax = EASY_HEART_RATE_MAX,
    )

    private fun warmup() = walk("warmup", "Ходьба перед бегом", 300, "Начните спокойно.")

    private fun run(month: Int, day: Int, minutes: Int, title: String, note: String) = session(
        month, day, title, "RUN", minutes + 10, 3, "$EASY_RUN $note",
        listOf(
            warmup(),
            easy("easy-run", "Лёгкий бег", minutes, EASY_RUN_TECHNIQUE),
            walk("cooldown", "Ходьба после бега", 300, "Спокойно восстановите дыхание."),
        ),
    )

    private fun stridesRun(month: Int, day: Int, firstMinutes: Int, strides: Int, lastMinutes: Int): PlanSession {
        val total = firstMinutes + lastMinutes + (strides * 110 + 30) / 60
        val block = "Ускорения"
        return session(
            month, day, "Лёгкий бег + ускорения $strides × 20 сек", "RUN", total + 10, 3,
            "$EASY_RUN Ускорения плавные, не спринт. При усталости пропустите их.",
            listOf(
                warmup(),
                easy("easy-run", "Лёгкий бег", firstMinutes, EASY_RUN_TECHNIQUE),
                ExerciseStep("stride", "Плавное ускорение", "$strides × 20 сек · не спринт",
                    "Плавно наберите скорость до быстрой, но расслабленной. На пульс не смотрите. " +
                        "На дорожке: 12–13 км/ч, не держась за поручни.",
                    illustrationKey = "stride", blockId = "strides", blockTitle = block,
                    blockType = WorkoutBlockType.CIRCUIT, rounds = strides, workSeconds = 20),
                ExerciseStep("stride-recovery", "Трусца или шаг", "90 сек после каждого ускорения",
                    "Полностью восстановите дыхание.",
                    illustrationKey = "stride-recovery", blockId = "strides", blockTitle = block,
                    blockType = WorkoutBlockType.CIRCUIT, rounds = strides, workSeconds = 90),
                easy("easy-run-finish", "Лёгкий бег", lastMinutes, EASY_RUN_TECHNIQUE),
                walk("cooldown", "Ходьба после бега", 300, "Спокойно восстановите дыхание."),
            ),
        )
    }

    private fun lift(
        id: String, title: String, dose: String, note: String, sets: Int, reps: Int, rest: Int, illustration: String,
        tags: List<String> = emptyList(),
    ) = ExerciseStep(id, title, dose, note, restSeconds = rest, restrictionTags = tags, exerciseId = id,
        illustrationKey = illustration, blockId = id, blockTitle = title, sets = sets, reps = reps)

    private fun circuit(
        id: String, title: String, dose: String, note: String, blockId: String, blockTitle: String, rest: Int,
        illustration: String, reps: Int? = null, seconds: Int? = null, tags: List<String> = emptyList(),
        restAfterRound: Int = rest,
    ) = ExerciseStep(id, title, dose, note, restSeconds = rest, restrictionTags = tags, exerciseId = id,
        illustrationKey = illustration, blockId = blockId, blockTitle = "$blockTitle · 2 круга",
        blockType = WorkoutBlockType.CIRCUIT, rounds = 2, reps = reps, workSeconds = seconds,
        restAfterRoundSeconds = restAfterRound)

    private fun strength(month: Int, day: Int, sets: Int, squatReps: Int, stepReps: Int, light: Boolean = false) = session(
        month, day, if (light) "Дом · облегчённая сила и core" else "Дом · сила ног, верх и core", "STRENGTH",
        if (light) 40 else 60, if (light) 4 else 6,
        "Не до отказа: 3–4 повтора в запасе. Опускание медленное — это подготовка к спускам. " +
            "Без боли в колене и плече. Подтягивания и планку пропустите, если делали утром.",
        listOf(
            walk("warmup", "Разминка", 480,
                "Спокойная ходьба, комфортная подвижность суставов и несколько пробных приседаний без веса."),
            lift("hinge", "Тяга гири с пола", "$sets × 10 · 16 кг",
                "Гиря между стопами, таз назад, спина нейтральна, гиря близко к телу. Встаньте за счёт ног и ягодиц, " +
                    "без рывков и боли в плече.", sets, 10, 90, "hip-hinge", listOf("SHOULDER_CLEARANCE_REQUIRED")),
            lift("split-squat", "Сплит-присед", "$sets × $squatReps на ногу · медленно вниз",
                "Устойчивая стойка, корпус вертикально, колено по направлению стопы. Три секунды вниз, " +
                    "без боли в колене.", sets, squatReps, 75, "split-squat"),
            lift("step-down", "Спуск со ступени", "$sets × $stepReps на ногу · медленно вниз",
                "Низкая устойчивая ступень. Медленно опускайте пятку свободной ноги к полу и возвращайтесь; " +
                    "таз ровно, колено не заваливается внутрь. Четыре секунды вниз. Без боли сзади колена.",
                sets, stepReps, 60, "step-down"),
            circuit("pull-up", "Подтягивания", "2 × 4–5 · вместо утренних",
                "Без раскачивания, рывков и отказа; только в безболезненной амплитуде. Если уже подтягивались " +
                    "утром — пропустите этот шаг.", "upper", "Верх тела", 90, "pull-up", reps = 5,
                tags = listOf("HANGING")),
            circuit("push-up", "Отжимания", "2 × 6–8 · без боли",
                "Локти примерно под 30–45° к корпусу, голова, корпус и таз на одной линии. Закончите до отказа; " +
                    "боль — повод прекратить подход.", "upper", "Верх тела", 90, "", reps = 8,
                tags = listOf("PUSH_UP")),
            circuit("calf", "Подъём на носок одной ногой", "2 × 12 на ногу",
                "Лёгкая опора для равновесия, полная амплитуда, три секунды вниз, без пружинящих движений.",
                "core", "Икры и core", 60, "calf-raise", reps = 12),
            circuit("bridge", "Ягодичный мост на одной ноге", "2 × 10 на ногу",
                "Таз ровный, пауза вверху, не прогибайте поясницу.", "core", "Икры и core", 60, "glute-bridge",
                reps = 10),
            circuit("core", "Dead bug", "2 × 8 на сторону",
                "Лёжа на спине, удерживайте рёбра и таз, плавно двигайте ногами без прогиба поясницы.",
                "core", "Икры и core", 60, "dead-bug-legs", reps = 8),
            circuit("forearm-plank", "Планка на предплечьях", "2 × 40 сек · вместо утренней",
                "Локти под плечами, рёбра и таз под контролем, без задержки дыхания. Только без боли в плече. " +
                    "Если планка уже выполнена утром — пропустите.", "core", "Икры и core", 60, "forearm-plank",
                seconds = 40, tags = listOf("PLANK"), restAfterRound = 90),
            walk("cooldown", "Завершение", 180,
                "Ходьба и восстановление дыхания. Следующим утром оцените колено и плечо; не добавляйте подходы."),
        ),
    )
}
