package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.PlanEnvelope
import ru.yakovenko.mountainform.data.PlanSession
import ru.yakovenko.mountainform.data.WorkoutBlockType
import java.time.LocalDate

/**
 * Agreed home-only recovery/base week after the 6 September 10 km run.
 *
 * The block deliberately avoids presses, dips, push-ups, overhead work and
 * unstable shoulder-supported positions. The 16 kg kettlebell is used only
 * close to the body and must be put aside if even the static hold hurts.
 */
object HomeKettlebellWeekPlan {
    private val availableFrom = LocalDate.of(2026, 9, 6)
    private val startDate = LocalDate.of(2026, 9, 8)
    private val endDate = LocalDate.of(2026, 9, 13)

    fun isRelevant(
        today: LocalDate,
        existingPlannedSessions: Map<String, Long>,
        completedEpochDays: Set<Long> = emptySet(),
    ): Boolean {
        if (today.isBefore(availableFrom) || today.isAfter(endDate)) return false
        val replacementFrom = maxOf(startDate, today).toEpochDay()
        val replacementThrough = endDate.toEpochDay()
        val expectedIds = sessions()
            .filter {
                !LocalDate.ofEpochDay(it.plannedEpochDay).isBefore(maxOf(startDate, today)) &&
                    it.plannedEpochDay !in completedEpochDays
            }
            .mapTo(mutableSetOf()) { it.id }
        val actualIds = existingPlannedSessions
            .filterValues { it in replacementFrom..replacementThrough && it !in completedEpochDays }
            .keys
        return expectedIds.isNotEmpty() && actualIds != expectedIds
    }

    fun envelope(
        today: LocalDate = LocalDate.now(),
        generatedAtEpochMillis: Long = System.currentTimeMillis(),
        completedEpochDays: Set<Long> = emptySet(),
    ): PlanEnvelope {
        val replaceFrom = maxOf(startDate, today)
        val futureSessions = sessions().filter {
            !LocalDate.ofEpochDay(it.plannedEpochDay).isBefore(replaceFrom) &&
                it.plannedEpochDay !in completedEpochDays
        }
        return PlanEnvelope(
            planId = "home-kettlebell-week-v1-${replaceFrom.toEpochDay()}",
            author = "Горная форма · согласовано в чате",
            reason = "Домашняя неделя 8–13 сентября после трёх беговых тренировок за шесть дней: беговой объём не растёт, работа ног усложняется гирей 16 кг, одноножными движениями и медленным опусканием. Жимы, отжимания, брусья и движения над головой исключены. План меняет только будущие занятия после просмотра.",
            generatedAtEpochMillis = generatedAtEpochMillis,
            replacePlannedFromEpochDay = replaceFrom.toEpochDay(),
            replacePlannedThroughEpochDay = endDate.toEpochDay(),
            sessions = futureSessions,
        )
    }

    private fun sessions(): List<PlanSession> = listOf(
        easyRunAndCore(LocalDate.of(2026, 9, 8)),
        recoveryRun(LocalDate.of(2026, 9, 10)),
        kettlebellStrength(LocalDate.of(2026, 9, 11)),
        longRun(LocalDate.of(2026, 9, 13)),
    )

    private fun easyRunAndCore(date: LocalDate) = PlanSession(
        id = "home-week-hybrid-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Лёгкий бег + домашний core",
        type = "HYBRID",
        phase = "Домашняя база",
        objective = "Восстановиться после 10 км и поддержать стабильность корпуса без нагрузки на плечо",
        durationMinutes = 60,
        targetRpe = 4,
        steps = listOf(
            aerobicStep("warmup", "Разминка ходьбой", "5 минут", "walk", 300, "Разминка"),
            aerobicStep(
                "easy-run", "Лёгкий непрерывный бег", "35 минут · RPE 3–4 · разговорный темп",
                "run-walk", 2_100, "Бег",
                "Не повторяйте темп воскресных 10 км: дыхание должно позволять говорить фразами.",
            ),
            aerobicStep("cooldown", "Заминка ходьбой", "5 минут", "walk", 300, "Заминка"),
            ExerciseStep(
                id = "bridge", title = "Ягодичный мост", prescription = "3 × 12 · пауза 2 секунды",
                instructions = "Руки лежат в безболезненном положении; не отталкивайтесь плечами.",
                exerciseId = "bridge", illustrationKey = "glute-bridge", blockId = "short-core",
                blockTitle = "Короткий круг", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 3, reps = 12, restSeconds = 20,
            ),
            ExerciseStep(
                id = "core", title = "Антиразгибание лёжа", prescription = "3 × 8 на сторону",
                instructions = "Удерживайте рёбра и таз неподвижными, плечи расслаблены.",
                exerciseId = "core", illustrationKey = "dead-bug-legs", blockId = "short-core",
                blockTitle = "Короткий круг", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 3, reps = 8, restAfterRoundSeconds = 60,
            ),
        ),
    )

    private fun recoveryRun(date: LocalDate) = PlanSession(
        id = "home-week-recovery-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Опциональный восстановительный бег",
        type = "RUN",
        phase = "Домашняя база",
        objective = "Добавить третий лёгкий бег только при нормальном восстановлении",
        durationMinutes = 35,
        targetRpe = 3,
        steps = listOf(
            aerobicStep("warmup", "Разминка ходьбой", "5 минут", "walk", 300, "Разминка"),
            aerobicStep(
                "easy-run", "Очень лёгкий бег или бег / ходьба", "25 минут · RPE 2–3",
                "run-walk", 1_500, "Восстановление",
                "Пропустите тренировку при усталости, боли или недовосстановлении перед силовой работой.",
            ),
            aerobicStep("cooldown", "Заминка ходьбой", "5 минут", "walk", 300, "Заминка"),
        ),
    )

    private fun kettlebellStrength(date: LocalDate) = PlanSession(
        id = "home-week-strength-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Домашние ноги с гирей + core",
        type = "STRENGTH",
        phase = "Домашняя база",
        objective = "Сделать ноги ощутимо сильнее без штанги и без провокации плеча",
        durationMinutes = 80,
        targetRpe = 6,
        steps = listOf(
            aerobicStep("warmup", "Ходьба и динамическая разминка", "8 минут", "walk", 480, "Разминка"),
            ExerciseStep(
                id = "kettlebell-deadlift", title = "Тяга гири с пола", prescription = "4 × 8–10 · 16 кг · RPE 6",
                instructions = "Гиря между стопами, руки висят нейтрально, таз уходит назад. Отложите гирю при боли или напряжении в плече.",
                restrictionTags = listOf("SHOULDER_CLEARANCE_REQUIRED"),
                exerciseId = "hinge", illustrationKey = "hip-hinge", blockId = "home-strength",
                blockTitle = "Сила ног", sets = 4, reps = 10, restSeconds = 90,
            ),
            ExerciseStep(
                id = "slow-box-squat", title = "Медленный присед до стула", prescription = "4 × 10 · 4 секунды вниз",
                instructions = "Не падайте на стул. Если гирю можно безболезненно держать у груди, выполните 8 повторов с 16 кг; иначе — 10 со своим весом.",
                exerciseId = "box-squat", illustrationKey = "box-squat", blockId = "home-strength",
                blockTitle = "Сила ног", sets = 4, reps = 10, restSeconds = 75,
            ),
            ExerciseStep(
                id = "step-down", title = "Медленное зашагивание вниз", prescription = "3 × 10 на ногу · 3 секунды вниз",
                instructions = "Низкая устойчивая ступень. Прекратите при боли сзади колена.",
                exerciseId = "step-down", illustrationKey = "step-down", blockId = "leg-circuit",
                blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 3, reps = 10, restSeconds = 20,
            ),
            ExerciseStep(
                id = "single-bridge", title = "Ягодичный мост на одной ноге", prescription = "3 × 10 на ногу",
                instructions = "Таз не разворачивается, руки лежат в безболезненном положении.",
                exerciseId = "bridge", illustrationKey = "glute-bridge", blockId = "leg-circuit",
                blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 3, reps = 10, restSeconds = 20,
            ),
            ExerciseStep(
                id = "single-calf", title = "Подъём на носок одной ногой", prescription = "3 × 12–15 на ногу",
                instructions = "Лёгкая опора рукой только для равновесия; опускайтесь медленно.",
                exerciseId = "calf", illustrationKey = "calf-raise", blockId = "leg-circuit",
                blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 3, reps = 15, restSeconds = 20,
            ),
            ExerciseStep(
                id = "core", title = "Антиразгибание лёжа", prescription = "3 × 10 на сторону",
                instructions = "Рёбра и таз неподвижны, плечи расслаблены.",
                exerciseId = "core", illustrationKey = "dead-bug-legs", blockId = "leg-circuit",
                blockTitle = "Спуски и core", blockType = WorkoutBlockType.CIRCUIT,
                rounds = 3, reps = 10, restAfterRoundSeconds = 75,
            ),
        ),
    )

    private fun longRun(date: LocalDate) = PlanSession(
        id = "home-week-long-${date.toEpochDay()}",
        plannedEpochDay = date.toEpochDay(),
        title = "Длинный лёгкий бег",
        type = "RUN",
        phase = "Домашняя база",
        objective = "Поддержать время на ногах, не прогрессируя одновременно темп и дистанцию",
        durationMinutes = 70,
        targetRpe = 4,
        steps = listOf(
            aerobicStep("warmup", "Разминка ходьбой", "5 минут", "walk", 300, "Разминка"),
            aerobicStep(
                "easy-run", "Лёгкий непрерывный бег", "50–55 минут · RPE 3–4 · разговорный темп",
                "run-walk", 3_300, "Основной бег",
                "Бегите медленнее 10 км 6 сентября. Перейдите на шаг при боли, необычной одышке или ухудшении техники.",
            ),
            aerobicStep("cooldown", "Заминка ходьбой", "10 минут", "walk", 600, "Заминка"),
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
