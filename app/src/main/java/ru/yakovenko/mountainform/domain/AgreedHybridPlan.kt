package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

object AgreedHybridPlan {
    private val startDate = LocalDate.of(2026, 8, 22)
    private val endDate = LocalDate.of(2026, 8, 30)

    val sessionIds = setOf(
        "hybrid-easy-${startDate.toEpochDay()}",
        "hybrid-run-core-${LocalDate.of(2026, 8, 25).toEpochDay()}",
        "hybrid-strength-${LocalDate.of(2026, 8, 28).toEpochDay()}",
        "hybrid-long-run-${endDate.toEpochDay()}",
    )

    fun isRelevant(today: LocalDate, existingSessionIds: Set<String>): Boolean =
        today.isBefore(LocalDate.of(2026, 8, 30)) && !existingSessionIds.containsAll(sessionIds)

    fun envelope(generatedAtEpochMillis: Long = System.currentTimeMillis()): PlanEnvelope = PlanEnvelope(
        planId = "agreed-hybrid-${startDate.toEpochDay()}",
        author = "Горная форма · согласовано в чате",
        reason = "Персональный переходный блок после двух беговых тренировок: две лёгкие пробежки, одна силовая для ног и core, одна более длинная лёгкая пробежка. Плечо остаётся активным ограничением; гиря, висы, упоры, нагрузка над головой и рюкзак исключены. План применяется только после подтверждения.",
        generatedAtEpochMillis = generatedAtEpochMillis,
        replacePlannedFromEpochDay = LocalDate.of(2026, 8, 21).toEpochDay(),
        replacePlannedThroughEpochDay = endDate.toEpochDay(),
        sessions = listOf(
            easyRun22(),
            easyRunAndCore25(),
            shoulderSafeStrength28(),
            longEasyRun30(),
        ),
    )

    private fun easyRun22() = PlanSession(
        id = "hybrid-easy-${startDate.toEpochDay()}",
        plannedEpochDay = startDate.toEpochDay(),
        title = "Короткий лёгкий бег",
        type = "RUN",
        phase = "Переход к гибридной базе",
        objective = "Спокойно проверить восстановление после 10 км без накопления усталости",
        durationMinutes = 30,
        targetRpe = 3,
        steps = listOf(
            ExerciseStep(
                id = "walk-warmup",
                title = "Разминка ходьбой / очень лёгким бегом",
                prescription = "10 минут · пульс до 140",
                instructions = "Ровная поверхность, свободное дыхание, плечи расслаблены.",
                exerciseId = "walk",
                illustrationKey = "walk",
                blockId = "warmup",
                blockTitle = "Разминка",
                blockType = WorkoutBlockType.AEROBIC,
                workSeconds = 600,
            ),
            ExerciseStep(
                id = "easy-run",
                title = "Лёгкий непрерывный бег",
                prescription = "15 минут · преимущественно 140–150 уд/мин",
                instructions = "Без цели по темпу. Сохраняйте разговорное усилие; перейдите на шаг при росте пульса или симптомов.",
                exerciseId = "run-walk",
                illustrationKey = "run-walk",
                blockId = "run",
                blockTitle = "Беговой блок",
                blockType = WorkoutBlockType.AEROBIC,
                workSeconds = 900,
            ),
            ExerciseStep(
                id = "walk-cooldown",
                title = "Заминка ходьбой",
                prescription = "5 минут",
                instructions = "Снизьте пульс постепенно; отметьте ощущения в колене и плече сразу и следующим утром.",
                exerciseId = "walk",
                illustrationKey = "walk",
                blockId = "cooldown",
                blockTitle = "Заминка",
                blockType = WorkoutBlockType.AEROBIC,
                workSeconds = 300,
            ),
        ),
    )

    private fun easyRunAndCore25(): PlanSession {
        val date = LocalDate.of(2026, 8, 25)
        return PlanSession(
            id = "hybrid-run-core-${date.toEpochDay()}",
            plannedEpochDay = date.toEpochDay(),
            title = "Лёгкий бег + core",
            type = "HYBRID",
            phase = "Переход к гибридной базе",
            objective = "Развивать аэробную базу и стабилизацию без нагрузки на плечо",
            durationMinutes = 50,
            targetRpe = 4,
            steps = listOf(
                ExerciseStep(
                    "walk-warmup", "Разминка ходьбой", "5 минут · пульс до 140", "Начните спокойно на ровной поверхности.",
                    exerciseId = "walk", illustrationKey = "walk", blockId = "warmup", blockTitle = "Разминка",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 300,
                ),
                ExerciseStep(
                    "easy-run", "Лёгкий непрерывный бег", "25 минут · преимущественно 140–150 уд/мин",
                    "Без цели по темпу; разговорное усилие. При симптомах перейдите на шаг.",
                    exerciseId = "run-walk", illustrationKey = "run-walk", blockId = "run", blockTitle = "Беговой блок",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 1500,
                ),
                ExerciseStep(
                    "walk-cooldown", "Заминка ходьбой", "5 минут", "Снижайте темп постепенно.",
                    exerciseId = "walk", illustrationKey = "walk", blockId = "cooldown", blockTitle = "Заминка",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 300,
                ),
                ExerciseStep(
                    "bridge", "Ягодичный мост", "2 × 10", "Руки лежат в безболезненном положении; не отталкивайтесь плечами.",
                    restSeconds = 15, exerciseId = "bridge", illustrationKey = "glute-bridge", blockId = "core",
                    blockTitle = "Core после бега", blockType = WorkoutBlockType.CIRCUIT, rounds = 2, reps = 10,
                ),
                ExerciseStep(
                    "core", "Антиразгибание лёжа", "2 × 6 на сторону", "Руки оставьте расслабленными; удерживайте рёбра и таз неподвижными.",
                    exerciseId = "core", illustrationKey = "dead-bug-legs", blockId = "core", blockTitle = "Core после бега",
                    blockType = WorkoutBlockType.CIRCUIT, rounds = 2, reps = 6, restAfterRoundSeconds = 45,
                ),
            ),
        )
    }

    private fun shoulderSafeStrength28(): PlanSession {
        val date = LocalDate.of(2026, 8, 28)
        return PlanSession(
            id = "hybrid-strength-${date.toEpochDay()}",
            plannedEpochDay = date.toEpochDay(),
            title = "Ноги + core без нагрузки на плечо",
            type = "STRENGTH",
            phase = "Переход к гибридной базе",
            objective = "Сохранять силу ног и устойчивость корпуса, не вовлекая болезненное плечо",
            durationMinutes = 65,
            targetRpe = 5,
            steps = listOf(
                ExerciseStep(
                    "bike", "Велотренажёр без опоры на руки", "10 минут легко", "Руки свободны и расслаблены; сопротивление умеренное.",
                    exerciseId = "bike", illustrationKey = "stationary-bike", blockId = "warmup", blockTitle = "Разминка",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 600,
                ),
                ExerciseStep(
                    "box-squat", "Присед до высокой опоры", "3 × 8 · RPE 5", "Только с весом тела, руки свободны; глубина без дискомфорта в колене.",
                    restSeconds = 90, exerciseId = "box-squat", illustrationKey = "box-squat", blockId = "squat",
                    blockTitle = "Основная сила", sets = 3, reps = 8,
                ),
                ExerciseStep(
                    "bridge", "Ягодичный мост", "3 × 10 · RPE 5", "Руки лежат без боли; пауза в верхней точке, без переразгибания поясницы.",
                    restSeconds = 75, exerciseId = "bridge", illustrationKey = "glute-bridge", blockId = "bridge",
                    blockTitle = "Основная сила", sets = 3, reps = 10,
                ),
                ExerciseStep(
                    "calf", "Подъём на носки", "3 × 12", "Не переносите вес на руки; опора только для равновесия.",
                    restSeconds = 15, exerciseId = "calf", illustrationKey = "calf-raise", blockId = "accessory",
                    blockTitle = "Икры и core по кругу", blockType = WorkoutBlockType.CIRCUIT, rounds = 3, reps = 12,
                ),
                ExerciseStep(
                    "core", "Антиразгибание лёжа", "3 × 8 на сторону", "Руки расслаблены; поясница не прогибается.",
                    exerciseId = "core", illustrationKey = "dead-bug-legs", blockId = "accessory",
                    blockTitle = "Икры и core по кругу", blockType = WorkoutBlockType.CIRCUIT, rounds = 3, reps = 8,
                    restAfterRoundSeconds = 60,
                ),
                ExerciseStep(
                    "walk-cooldown", "Спокойная ходьба", "10–15 минут легко", "Без уклона вниз и без рюкзака.",
                    exerciseId = "walk", illustrationKey = "walk", blockId = "cooldown", blockTitle = "Заминка",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 600,
                ),
            ),
        )
    }

    private fun longEasyRun30(): PlanSession {
        val date = endDate
        return PlanSession(
            id = "hybrid-long-run-${date.toEpochDay()}",
            plannedEpochDay = date.toEpochDay(),
            title = "Длинный лёгкий бег",
            type = "RUN",
            phase = "Переход к гибридной базе",
            objective = "Увеличить спокойное непрерывное время на ногах без цели повторить 10 км",
            durationMinutes = 50,
            targetRpe = 4,
            steps = listOf(
                ExerciseStep(
                    "walk-warmup", "Разминка ходьбой", "5 минут · пульс до 140", "Ровная поверхность, спокойное начало.",
                    exerciseId = "walk", illustrationKey = "walk", blockId = "warmup", blockTitle = "Разминка",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 300,
                ),
                ExerciseStep(
                    "easy-run", "Лёгкий непрерывный бег", "35 минут · преимущественно не выше 150 уд/мин",
                    "Без цели по дистанции и темпу; разговорное усилие, короткий естественный шаг.",
                    exerciseId = "run-walk", illustrationKey = "run-walk", blockId = "run", blockTitle = "Основной бег",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 2100,
                ),
                ExerciseStep(
                    "optional-finish", "Опциональное продолжение", "До 5 минут · не выше 155 уд/мин", "Выполняйте только если бег всё ещё ощущается лёгким и симптомов нет.",
                    required = false, exerciseId = "run-walk", illustrationKey = "run-walk", blockId = "finish",
                    blockTitle = "Только при хорошем самочувствии", blockType = WorkoutBlockType.AEROBIC, workSeconds = 300,
                ),
                ExerciseStep(
                    "walk-cooldown", "Заминка ходьбой", "5 минут", "Отметьте ощущения сразу и следующим утром.",
                    exerciseId = "walk", illustrationKey = "walk", blockId = "cooldown", blockTitle = "Заминка",
                    blockType = WorkoutBlockType.AEROBIC, workSeconds = 300,
                ),
            ),
        )
    }
}
