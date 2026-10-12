package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/** Explicitly agreed dated proposal; never applied by an APK update. */
object BaseOneBlock {
    const val PLAN_ID = "base-one-2026-10-13-v2"

    /** The same block exported as a file before heart-rate ceilings existed; this revision supersedes it. */
    const val SUPERSEDED_FILE_PLAN_ID = "base1-2026-10-13-v1"
    const val EASY_HEART_RATE_MAX = 148
    val availableFrom: LocalDate = LocalDate.of(2026, 10, 12)
    val through: LocalDate = LocalDate.of(2026, 11, 15)

    private const val PHASE = "База 1 · лёгкий бег"
    private const val HEART_RATE_NOTE = "Пульс до $EASY_HEART_RATE_MAX — верхняя граница, а не цель: если он выше, " +
        "замедлитесь или перейдите на шаг, пока не опустится до 140. Темп не важен, разговор полными фразами."
    private const val STOP_NOTE = "При боли, которая меняет технику, или необычном самочувствии остановитесь."
    private const val LOW_READINESS = "Если утром готовность на часах низкая или вариабельность пульса ниже нормы — " +
        "замените бег ходьбой 30 минут."
    private const val LONG_RUN = "Длительная: на подъёмах переходите на шаг. Если в последней трети пульс уходит " +
        "выше 150 — минута шага на каждые девять минут бега."
    private const val AFTER_STRENGTH = "После силовой ноги могут быть тяжёлыми — выберите ровный маршрут."
    private const val DELOAD = "Разгрузочная неделя: объём снижен намеренно, не добавляйте."
    private const val TREADMILL = "В плохую погоду — на дорожке: уклон 1%, скорость подбирайте по пульсу " +
        "(ориентир 8,3–9,0 км/ч)."
    private const val TREADMILL_STRIDES = "На дорожке ускорение — 20 секунд на 12–13 км/ч, не держась за поручни; " +
        "если неудобно, пропустите ускорения."

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
        } else "") + "13 октября — 15 ноября. Первый блок подготовки к весеннему полумарафону и осеннему марафону. " +
            "Весь бег блока лёгкий, с верхней границей пульса $EASY_HEART_RATE_MAX; темп не задаётся. " +
            "Три пробежки и одна домашняя силовая в неделю: вторник, среда, пятница, воскресенье. " +
            "Объём растёт со 110 до 170 минут бега в неделю, 9–15 ноября — разгрузка. " +
            "Короткие ускорения появляются с 27 октября. Силовая — с акцентом на медленное опускание " +
            "для подготовки к спускам. Утренние подтягивания и планка входят в общий объём. " +
            "При боли, отёке, хромоте, болезни или ухудшении на следующий день нагрузку сократить либо пропустить; " +
            "пропущенное не компенсировать. Ограничения профиля и история выполненных занятий сохраняются.",
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

    private fun warmup() = walk("warmup", "Ходьба перед бегом", 300,
        "Начните спокойно; включите на часах оповещение о пульсе.")

    private fun run(month: Int, day: Int, minutes: Int, title: String, note: String) = session(
        month, day, title, "RUN", minutes + 10, 3,
        "Бег $minutes минут; ещё 10 минут ходьбы включены во время карточки. " +
            "Пульс не выше $EASY_HEART_RATE_MAX, темп любой. $note $TREADMILL",
        listOf(
            warmup(),
            easy("easy-run", "Лёгкий бег", minutes, "$HEART_RATE_NOTE $STOP_NOTE"),
            walk("cooldown", "Ходьба после бега", 300, "Спокойно восстановите дыхание, не добавляйте дистанцию."),
        ),
    )

    private fun stridesRun(month: Int, day: Int, firstMinutes: Int, strides: Int, lastMinutes: Int): PlanSession {
        val total = firstMinutes + lastMinutes + (strides * 110 + 30) / 60
        val block = "Ускорение / лёгкий бег"
        return session(
            month, day, "Лёгкий бег + ускорения $strides × 20 сек", "RUN", total + 10, 3,
            "Около $total минут бега вместе с ускорениями; ещё 10 минут ходьбы включены во время карточки. " +
                "Основной бег — пульс не выше $EASY_HEART_RATE_MAX. Ускорения плавные, не спринт; " +
                "при усталости или боли пропустите их и просто бегите легко. $TREADMILL $TREADMILL_STRIDES",
            listOf(
                warmup(),
                easy("easy-run", "Лёгкий бег", firstMinutes, "$HEART_RATE_NOTE $STOP_NOTE"),
                ExerciseStep("stride", "Плавное ускорение", "$strides × 20 сек · не спринт",
                    "На ровном участке плавно наберите скорость до быстрой, но расслабленной. " +
                        "На пульс в ускорениях не смотрите.",
                    illustrationKey = "stride", blockId = "strides", blockTitle = block,
                    blockType = WorkoutBlockType.CIRCUIT, rounds = strides, workSeconds = 20),
                ExerciseStep("stride-recovery", "Очень лёгкий бег или шаг", "90 сек после каждого ускорения",
                    "Полностью восстановите дыхание перед следующим ускорением.",
                    illustrationKey = "stride-recovery", blockId = "strides", blockTitle = block,
                    blockType = WorkoutBlockType.CIRCUIT, rounds = strides, workSeconds = 90),
                easy("easy-run-finish", "Лёгкий бег после ускорений", lastMinutes, HEART_RATE_NOTE),
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
        "${if (light) "35–40" else "55–60"} минут, усилие ${if (light) "4–5" else "6"}/10, не до отказа: оставляйте " +
            "3–4 повтора в запасе. Акцент — медленное опускание в сплит-приседе и спуске со ступени: это подготовка " +
            "к спускам. Только без боли сзади колена и в плече во время занятия и следующим утром. " +
            "Не дублируйте утренние подтягивания и планку: если уже сделали — пропустите эти шаги.",
        listOf(
            walk("warmup", "Ходьба и разминка", 480,
                "Спокойная ходьба, комфортная подвижность суставов и несколько пробных приседаний без веса."),
            lift("hinge", "Тяга гири с пола", "$sets × 10 · гиря 16 кг",
                "Гиря между стопами, таз назад, спина нейтральна, гиря близко к телу. Встаньте за счёт ног и ягодиц, " +
                    "без рывков и боли в плече.", sets, 10, 90, "hip-hinge", listOf("SHOULDER_CLEARANCE_REQUIRED")),
            lift("split-squat", "Сплит-присед без веса", "$sets × $squatReps на ногу · медленно вниз",
                "Устойчивая стойка, корпус вертикально, колено по направлению стопы. Три секунды вниз, " +
                    "без боли в колене.", sets, squatReps, 75, "split-squat"),
            lift("step-down", "Контролируемый спуск со ступени", "$sets × $stepReps на ногу · медленно вниз",
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
            circuit("forearm-plank", "Планка на предплечьях", "2 × 40 секунд · вместо утренней",
                "Локти под плечами, рёбра и таз под контролем, без задержки дыхания. Только без боли в плече. " +
                    "Если планка уже выполнена утром — пропустите.", "core", "Икры и core", 60, "forearm-plank",
                seconds = 40, tags = listOf("PLANK"), restAfterRound = 90),
            walk("cooldown", "Спокойное завершение", 180,
                "Ходьба и восстановление дыхания. Следующим утром оцените колено и плечо; не добавляйте подходы."),
        ),
    )
}
