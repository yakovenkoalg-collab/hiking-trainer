package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/** Explicitly agreed dated proposal; never applied by an APK update. */
object BaseOneBlock {
    const val PLAN_ID = "base-one-2026-10-13-v4"

    /** Earlier revisions of the same block (file export and the first bundled one); this revision supersedes them. */
    val SUPERSEDED_PLAN_IDS = setOf("base1-2026-10-13-v1", "base-one-2026-10-13-v2", "base-one-2026-10-13-v3")
    const val EASY_HEART_RATE_MAX = 148
    val availableFrom: LocalDate = LocalDate.of(2026, 10, 12)
    val through: LocalDate = LocalDate.of(2026, 11, 15)

    private const val PHASE = "База 1 · лёгкий бег"
    private const val EASY_RUN_TECHNIQUE = "Разговор полными фразами. Пульс выше $EASY_HEART_RATE_MAX — замедлитесь " +
        "или идите шагом, пока не опустится до 140. На дорожке: уклон 1%, 8,3–9,0 км/ч по пульсу. " +
        "При боли, которая меняет технику, остановитесь."
    const val CALM_START_HEART_RATE_MAX = 140
    private const val CALM_START_MINUTES = 10
    private const val EASY_RUN = "Первые 10 минут — пульс до $CALM_START_HEART_RATE_MAX, дальше до $EASY_HEART_RATE_MAX. " +
        "Выше — шаг, пока пульс не опустится."
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
            "Версия 4: с 4 ноября силовая тяжелее — махи и тяга гири на одной ноге, сплит-присед с гирей, " +
            "болгарский сплит-присед, больше подходов верха. Пробежки без ходьбы: первые 10 минут — спокойный " +
            "старт с пульсом до 140. Объём бега и даты не менялись. " +
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
        progressedStrength(11, 4, light = false),
        run(11, 6, 45, "Лёгкий бег · 45 мин", AFTER_STRENGTH),
        run(11, 8, 80, "Длительный лёгкий бег · 80 мин", "$LONG_RUN Возьмите воду."),

        run(11, 10, 35, "Лёгкий бег · 35 мин · разгрузка", DELOAD),
        progressedStrength(11, 11, light = true),
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
        id, title, minutesLabel(seconds / 60), instructions, exerciseId = "walk", illustrationKey = "walk",
        blockId = id, blockTitle = title, blockType = WorkoutBlockType.AEROBIC, workSeconds = seconds,
    )

    private fun easy(id: String, title: String, minutes: Int, instructions: String) = ExerciseStep(
        id, title, "$minutes минут · пульс до $EASY_HEART_RATE_MAX", instructions,
        exerciseId = "run-walk", illustrationKey = "run-walk", blockId = id, blockTitle = title,
        blockType = WorkoutBlockType.AEROBIC, workSeconds = minutes * 60, targetHeartRateMax = EASY_HEART_RATE_MAX,
    )

    /** Replaces walking before the run: the first minutes are running, just slower, so the pulse does not overshoot. */
    private fun calmStart() = ExerciseStep(
        "calm-start", "Спокойный старт", "$CALM_START_MINUTES минут · пульс до $CALM_START_HEART_RATE_MAX",
        "Не быстрее 7:00/км. Пульс отстаёт от нагрузки на несколько минут, поэтому низкие цифры в начале — " +
            "не повод ускоряться.",
        exerciseId = "run-walk", illustrationKey = "run-walk", blockId = "calm-start", blockTitle = "Спокойный старт",
        blockType = WorkoutBlockType.AEROBIC, workSeconds = CALM_START_MINUTES * 60,
        targetHeartRateMax = CALM_START_HEART_RATE_MAX,
    )

    private fun run(month: Int, day: Int, minutes: Int, title: String, note: String) = session(
        month, day, title, "RUN", minutes, 3, "$EASY_RUN $note",
        listOf(
            calmStart(),
            easy("easy-run", "Лёгкий бег", minutes - CALM_START_MINUTES, EASY_RUN_TECHNIQUE),
        ),
    )

    private fun stridesRun(month: Int, day: Int, firstMinutes: Int, strides: Int, lastMinutes: Int): PlanSession {
        val total = firstMinutes + lastMinutes + (strides * 110 + 30) / 60
        val block = "Ускорения"
        return session(
            month, day, "Лёгкий бег + ускорения $strides × 20 сек", "RUN", total, 3,
            "$EASY_RUN Ускорения плавные, не спринт. При усталости пропустите их.",
            listOf(
                calmStart(),
                easy("easy-run", "Лёгкий бег", firstMinutes - CALM_START_MINUTES, EASY_RUN_TECHNIQUE),
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
        rounds: Int = 2,
    ) = ExerciseStep(id, title, dose, note, restSeconds = rest, restrictionTags = tags, exerciseId = id,
        illustrationKey = illustration, blockId = blockId, blockTitle = "$blockTitle · $rounds круга",
        blockType = WorkoutBlockType.CIRCUIT, rounds = rounds, reps = reps, workSeconds = seconds,
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
            circuit("core", "Мёртвый жук", "2 × 8 на сторону",
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

    private fun minutesLabel(minutes: Int): String = "$minutes " + when {
        minutes % 100 in 11..14 -> "минут"
        minutes % 10 == 1 -> "минута"
        minutes % 10 in 2..4 -> "минуты"
        else -> "минут"
    }

    /** From 4 November: the home session the user outgrew, with load added in every block; matches the Garmin workout. */
    private fun progressedStrength(month: Int, day: Int, light: Boolean) = session(
        month, day, if (light) "Дом · сила, разгрузка" else "Дом · сила, прогрессия", "STRENGTH",
        if (light) 40 else 55, if (light) 4 else 6,
        if (light) "Разгрузка: меньше подходов. Усилие 4–5 из 10. Без боли в колене и плече."
        else "Нагрузка 6–7 из 10. Последние 2–3 повтора даются с трудом. " +
            "Опускание медленное, техника не ломается. Без боли в колене и плече.",
        buildList {
            val rounds = if (light) 2 else 3
            add(walk("warmup", "Разминка", 480,
                "Спокойная ходьба, подвижность суставов, несколько махов и приседаний без веса."))
            add(circuit("kettlebell-swing", "Махи гирей", "$rounds × 15 · 16 кг",
                "Наклон от таза с ровной спиной, гиря между бёдер, затем резкое разгибание таза — гиря поднимается " +
                    "до груди за счёт бёдер, а не рук.", "swing", "Махи и тяга", 30, "kettlebell-swing", reps = 15,
                tags = listOf("SHOULDER_CLEARANCE_REQUIRED"), restAfterRound = 90, rounds = rounds))
            add(circuit("single-leg-kb-deadlift", "Тяга гири на одной ноге",
                "$rounds × ${if (light) 6 else 8} на ногу · 16 кг",
                "Гиря в руке со стороны свободной ноги. Наклон от таза с ровной спиной, свободная нога назад на одной " +
                    "линии с корпусом; колено опорной ноги слегка согнуто.", "swing", "Махи и тяга", 30,
                "single-leg-kb-deadlift", reps = if (light) 6 else 8, tags = listOf("SHOULDER_CLEARANCE_REQUIRED"),
                restAfterRound = 90, rounds = rounds))
            add(lift("goblet-split-squat", "Сплит-присед с гирей у груди", "$rounds × 8 на ногу · 16 кг",
                "Гиря за рожки у груди, разножка, обе стопы на полу. Три секунды вниз, корпус прямо, колено над стопой.",
                rounds, 8, 90, "goblet-split-squat", listOf("SHOULDER_CLEARANCE_REQUIRED")))
            if (!light) add(lift("bulgarian-split-squat", "Болгарский сплит-присед", "3 × 8 на ногу · медленно вниз",
                "Подъём задней стопы на скамье или стуле. Три секунды вниз до бедра почти параллельно полу, " +
                    "колено над стопой.", 3, 8, 75, "bulgarian-split-squat"))
            add(lift("step-down", "Спуск со ступени", "2 × ${if (light) 8 else 10} на ногу · медленно вниз",
                "Ступень повыше, чем раньше. Четыре секунды вниз, таз ровно, колено не заваливается внутрь. " +
                    "Без боли сзади колена.", 2, if (light) 8 else 10, 60, "step-down"))
            add(circuit("pull-up", "Подтягивания", "$rounds × ${if (light) 6 else 8}",
                "Без раскачивания, рывков и отказа.", "upper", "Верх тела", 30, "pull-up",
                reps = if (light) 6 else 8, tags = listOf("HANGING"), restAfterRound = 90, rounds = rounds))
            add(circuit("push-up", "Отжимания", "$rounds × ${if (light) 15 else 20}",
                "Локти под 30–45° к корпусу, тело — прямая линия. Закончите до отказа.", "upper", "Верх тела", 30, "",
                reps = if (light) 15 else 20, tags = listOf("PUSH_UP"), restAfterRound = 90, rounds = rounds))
            add(circuit("calf", "Подъём на носок одной ногой", "2 × ${if (light) 12 else 15} на ногу",
                "Лёгкая опора для равновесия, полная амплитуда, три секунды вниз.", "core", "Икры и core", 30,
                "calf-raise", reps = if (light) 12 else 15))
            add(circuit("bridge", "Ягодичный мост на одной ноге", "2 × ${if (light) 10 else 12} на ногу",
                "Таз ровный, пауза вверху.", "core", "Икры и core", 30, "glute-bridge", reps = if (light) 10 else 12))
            add(circuit("core", "Мёртвый жук", "2 × 10 на сторону",
                "Лёжа на спине, рёбра и таз неподвижны, плавно двигайте руками и ногами.", "core", "Икры и core", 30,
                "dead-bug-legs", reps = 10))
            add(circuit("forearm-plank", "Планка на предплечьях", "2 × ${if (light) 45 else 60} сек",
                "Локти под плечами, тело — прямая линия, дыхание свободное.", "core", "Икры и core", 30,
                "forearm-plank", seconds = if (light) 45 else 60, tags = listOf("PLANK"), restAfterRound = 60))
            add(walk("cooldown", "Завершение", 180, "Ходьба и восстановление дыхания."))
        },
    )
}
