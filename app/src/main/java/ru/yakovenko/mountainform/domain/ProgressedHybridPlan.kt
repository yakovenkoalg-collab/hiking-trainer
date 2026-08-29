package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/**
 * Agreed progression after the 28 August bike + strength session.
 *
 * Upper-body work is deliberately generated only after the user records an
 * in-person clearance phase. Until then the plan stays useful and applyable,
 * but contains no hidden or automatically substituted shoulder load.
 */
object ProgressedHybridPlan {
    private val startDate = LocalDate.of(2026, 8, 30)
    private val endDate = LocalDate.of(2026, 9, 13)

    fun isRelevant(
        today: LocalDate,
        existingSessionIds: Set<String>,
        includeClearedUpperBody: Boolean,
        completedEpochDays: Set<Long> = emptySet(),
    ): Boolean {
        if (today.isAfter(endDate)) return false
        val expectedIds = sessions(includeClearedUpperBody)
            .filter {
                !LocalDate.ofEpochDay(it.plannedEpochDay).isBefore(today) &&
                    it.plannedEpochDay !in completedEpochDays
            }
            .mapTo(mutableSetOf()) { it.id }
        return expectedIds.isNotEmpty() && !existingSessionIds.containsAll(expectedIds)
    }

    fun envelope(
        includeClearedUpperBody: Boolean,
        today: LocalDate = LocalDate.now(),
        generatedAtEpochMillis: Long = System.currentTimeMillis(),
        completedEpochDays: Set<Long> = emptySet(),
    ): PlanEnvelope {
        val replaceFrom = maxOf(startDate, today)
        val futureSessions = sessions(includeClearedUpperBody).filter {
            !LocalDate.ofEpochDay(it.plannedEpochDay).isBefore(replaceFrom) &&
                it.plannedEpochDay !in completedEpochDays
        }
        val upperReason = if (includeClearedUpperBody) {
            "В две силовые добавлен щадящий блок верха после отмеченного очного разрешения: тяга с опорой груди и работа рук, без отжиманий, брусьев и движений над головой."
        } else {
            "Верх тела пока не включён: сначала нужно отметить очное разрешение специалиста в настройках ограничения плеча."
        }
        return PlanEnvelope(
            planId = "progressed-hybrid-${replaceFrom.toEpochDay()}-${if (includeClearedUpperBody) "upper" else "safe"}",
            author = "Горная форма · согласовано в чате",
            reason = "Двухнедельный блок после велосипеда и слишком лёгкой работы ног: две обязательные лёгкие пробежки и одна опциональная в неделю, одна прогрессивная силовая для ног и core. $upperReason Нагрузка предлагается к применению только после просмотра изменений.",
            generatedAtEpochMillis = generatedAtEpochMillis,
            replacePlannedFromEpochDay = replaceFrom.toEpochDay(),
            replacePlannedThroughEpochDay = endDate.toEpochDay(),
            sessions = futureSessions,
        )
    }

    private fun sessions(includeUpper: Boolean): List<PlanSession> = listOf(
        longRun(LocalDate.of(2026, 8, 30), 55, 40),
        runAndCore(LocalDate.of(2026, 9, 1), 55, 30, 2),
        recoveryRun(LocalDate.of(2026, 9, 3), 30),
        strength(LocalDate.of(2026, 9, 4), includeUpper, secondWeek = false),
        longRun(LocalDate.of(2026, 9, 6), 60, 45),
        runAndCore(LocalDate.of(2026, 9, 8), 60, 35, 3),
        recoveryRun(LocalDate.of(2026, 9, 10), 30),
        strength(LocalDate.of(2026, 9, 11), includeUpper, secondWeek = true),
        longRun(LocalDate.of(2026, 9, 13), 65, 50),
    )

    private fun longRun(date: LocalDate, totalMinutes: Int, runMinutes: Int) = PlanSession(
        id = "progress-long-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Длинный лёгкий бег",
        type = "RUN",
        phase = "Гибридная база",
        objective = "Увеличивать спокойное время на ногах без цели по темпу или дистанции",
        durationMinutes = totalMinutes,
        targetRpe = 4,
        steps = listOf(
            aerobicStep("warmup", "Разминка ходьбой", "5 минут", "walk", 300, "Разминка"),
            aerobicStep(
                "easy-run", "Лёгкий непрерывный бег", "$runMinutes минут · разговорный темп, RPE 3–4",
                "run-walk", runMinutes * 60, "Основной бег",
                "Темп свободный. Перейдите на шаг при боли, необычной одышке или заметном ухудшении техники.",
            ),
            aerobicStep("cooldown", "Заминка ходьбой", "10 минут", "walk", 600, "Заминка"),
        ),
    )

    private fun runAndCore(date: LocalDate, totalMinutes: Int, runMinutes: Int, rounds: Int) = PlanSession(
        id = "progress-hybrid-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Лёгкий бег + ноги и core",
        type = "HYBRID",
        phase = "Гибридная база",
        objective = "Поддерживать аэробную базу, стабильность корпуса и контроль ноги между силовыми днями",
        durationMinutes = totalMinutes,
        targetRpe = 4,
        steps = listOf(
            aerobicStep("warmup", "Разминка ходьбой", "5 минут", "walk", 300, "Разминка"),
            aerobicStep(
                "easy-run", "Лёгкий непрерывный бег", "$runMinutes минут · разговорный темп, RPE 3–4",
                "run-walk", runMinutes * 60, "Бег",
            ),
            aerobicStep("cooldown", "Заминка ходьбой", "5 минут", "walk", 300, "Заминка"),
            ExerciseStep(
                id = "bridge", title = "Ягодичный мост", prescription = "$rounds × 12",
                instructions = "Пауза вверху, руки лежат в безболезненном положении.",
                exerciseId = "bridge", illustrationKey = "glute-bridge", blockId = "core",
                blockTitle = "Короткий круг", blockType = WorkoutBlockType.CIRCUIT,
                rounds = rounds, reps = 12, restSeconds = 15,
            ),
            ExerciseStep(
                id = "core", title = "Антиразгибание лёжа", prescription = "$rounds × 8 на сторону",
                instructions = "Удерживайте рёбра и таз неподвижными, не напрягайте плечо.",
                exerciseId = "core", illustrationKey = "dead-bug-legs", blockId = "core",
                blockTitle = "Короткий круг", blockType = WorkoutBlockType.CIRCUIT,
                rounds = rounds, reps = 8, restAfterRoundSeconds = 45,
            ),
        ),
    )

    private fun recoveryRun(date: LocalDate, totalMinutes: Int) = PlanSession(
        id = "progress-optional-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Опциональный восстановительный бег",
        type = "RUN",
        phase = "Гибридная база",
        objective = "Третья короткая пробежка только при нормальном восстановлении",
        durationMinutes = totalMinutes,
        targetRpe = 3,
        steps = listOf(
            aerobicStep("warmup", "Разминка ходьбой", "5 минут", "walk", 300, "Разминка"),
            aerobicStep(
                "easy-run", "Очень лёгкий бег или бег / ходьба", "20 минут · RPE 2–3",
                "run-walk", 1200, "Восстановление",
                "Пропустите всю тренировку при усталости, боли или если силовая/длинный бег требуют восстановления.",
            ),
            aerobicStep("cooldown", "Заминка ходьбой", "5 минут", "walk", 300, "Заминка"),
        ),
    )

    private fun strength(date: LocalDate, includeUpper: Boolean, secondWeek: Boolean): PlanSession {
        val strengthId = "progress-strength-${date.toEpochDay()}${if (includeUpper) "-upper" else ""}"
        val mainSets = if (secondWeek) 4 else 3
        val steps = buildList {
            add(aerobicStep("bike", "Велотренажёр", "10 минут легко", "bike", 600, "Разминка"))
            add(
                ExerciseStep(
                    "leg-press", "Жим ногами", "$mainSets × 8–10 · RPE 6",
                    "Подберите вес так, чтобы последние два повтора ощущались рабочими, но техника оставалась чистой. При хорошем восстановлении прибавляйте только один параметр: 1 повтор или 2,5–5% веса.",
                    restSeconds = 120, exerciseId = "leg-press", blockId = "main", blockTitle = "Сила ног",
                    sets = mainSets, reps = 10,
                ),
            )
            add(
                ExerciseStep(
                    "leg-curl", "Сгибание ног в тренажёре", "3 × 10–12 · RPE 6",
                    "Опускайте вес за 2–3 секунды, таз не отрывайте.",
                    restSeconds = 90, exerciseId = "leg-curl", blockId = "main", blockTitle = "Сила ног",
                    sets = 3, reps = 12,
                ),
            )
            add(
                ExerciseStep(
                    "hip-thrust", "Ягодичный мост в тренажёре", "3 × 8–10 · RPE 6",
                    "Вес располагается на тазу; не отталкивайтесь шеей и плечами.",
                    restSeconds = 90, exerciseId = "hip-thrust-machine", blockId = "main", blockTitle = "Сила ног",
                    sets = 3, reps = 10,
                ),
            )
            add(
                ExerciseStep(
                    "step-down", "Медленное зашагивание вниз", "3 × 8 на ногу · 3 секунды вниз",
                    "Начните с низкой ступени. Остановитесь при боли сзади колена; опора рукой только для равновесия.",
                    restSeconds = 20, exerciseId = "step-down", blockId = "accessory",
                    blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT, rounds = 3, reps = 8,
                ),
            )
            add(
                ExerciseStep(
                    "calf", "Подъём на носки", "3 × 12–15",
                    "Пауза наверху, опускание под контролем.",
                    restSeconds = 15, exerciseId = "calf", illustrationKey = "calf-raise", blockId = "accessory",
                    blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT, rounds = 3, reps = 15,
                ),
            )
            add(
                ExerciseStep(
                    "core", "Антиразгибание лёжа", "3 × 8 на сторону",
                    "Руки расслаблены; поясница не прогибается.",
                    exerciseId = "core", illustrationKey = "dead-bug-legs", blockId = "accessory",
                    blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT,
                    rounds = 3, reps = 8, restAfterRoundSeconds = 75,
                ),
            )
            if (includeUpper) addAll(upperBodySteps())
        }
        return PlanSession(
            id = strengthId,
            plannedEpochDay = date.toEpochDay(),
            title = if (includeUpper) "Ноги + разрешённый верх + core" else "Усиленная работа ног + core",
            type = "STRENGTH",
            phase = "Гибридная база",
            objective = if (includeUpper) {
                "Прогрессировать силу ног и вернуть начальную работу верха в разрешённом диапазоне"
            } else {
                "Сделать работу ног ощутимо сложнее, сохраняя контроль колена и защиту плеча"
            },
            durationMinutes = if (includeUpper) 90 else 75,
            targetRpe = 6,
            steps = steps,
        )
    }

    private fun upperBodySteps() = listOf(
        ExerciseStep(
            "supported-row", "Горизонтальная тяга с упором груди", "2 × 10–12 · RPE 4–5",
            "Только в разрешённом специалистом диапазоне, без боли и без отведения локтей.",
            restSeconds = 75, restrictionTags = listOf("SHOULDER_CLEARANCE_REQUIRED"),
            exerciseId = "chest-supported-row", blockId = "upper-row", blockTitle = "Возврат верха: тяга",
            sets = 2, reps = 12,
        ),
        ExerciseStep(
            "biceps", "Сгибание рук на нижнем блоке", "2 × 10–12 · RPE 4–5",
            "Локти у корпуса, плечо остаётся неподвижным; прекратите при боли.",
            restSeconds = 20, restrictionTags = listOf("SHOULDER_CLEARANCE_REQUIRED"),
            exerciseId = "biceps-cable", blockId = "upper-arms", blockTitle = "Возврат верха: руки",
            blockType = WorkoutBlockType.CIRCUIT, rounds = 2, reps = 12,
        ),
        ExerciseStep(
            "triceps", "Разгибание рук на верхнем блоке", "2 × 10–12 · RPE 4–5",
            "Локти у корпуса, плечи расслаблены; прекратите при боли.",
            restSeconds = 20, restrictionTags = listOf("SHOULDER_CLEARANCE_REQUIRED"),
            exerciseId = "triceps-cable", blockId = "upper-arms", blockTitle = "Возврат верха: руки",
            blockType = WorkoutBlockType.CIRCUIT, rounds = 2, reps = 12, restAfterRoundSeconds = 75,
        ),
    )

    private fun aerobicStep(
        id: String,
        title: String,
        prescription: String,
        exerciseId: String,
        workSeconds: Int,
        blockTitle: String,
        instructions: String = "Начните спокойно и сохраняйте свободное дыхание.",
    ) = ExerciseStep(
        id = id,
        title = title,
        prescription = prescription,
        instructions = instructions,
        exerciseId = exerciseId,
        illustrationKey = exerciseId,
        blockId = id,
        blockTitle = blockTitle,
        blockType = WorkoutBlockType.AEROBIC,
        workSeconds = workSeconds,
    )
}
