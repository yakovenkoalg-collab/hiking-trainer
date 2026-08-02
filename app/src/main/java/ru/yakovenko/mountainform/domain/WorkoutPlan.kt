package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.WorkoutBlockType

data class WorkoutSetTarget(
    val step: ExerciseStep,
    val blockId: String,
    val blockTitle: String,
    val blockType: String,
    val roundIndex: Int,
    val setIndex: Int,
    val totalRounds: Int,
    val totalSets: Int,
    val plannedReps: Int?,
    val workSeconds: Int?,
    val restAfterSeconds: Int,
)

object WorkoutPlanCompiler {
    fun compile(steps: List<ExerciseStep>): List<WorkoutSetTarget> =
        steps.groupByPreservingOrder { it.blockId.ifBlank { it.id } }.flatMap { (_, blockSteps) ->
            val normalized = blockSteps.map(::normalizeLegacyStep)
            when (normalized.firstOrNull()?.blockType) {
                WorkoutBlockType.CIRCUIT, WorkoutBlockType.SUPERSET -> compileRounds(normalized)
                else -> compileStraight(normalized)
            }
        }

    fun normalizeLegacyStep(step: ExerciseStep): ExerciseStep {
        val parsedSets = Regex("""(?i)(\d+)\s*[×x]\s*""").find(step.prescription)?.groupValues?.get(1)?.toIntOrNull()
        val parsedReps = Regex("""(?i)\d+\s*[×x]\s*(\d+)""").find(step.prescription)?.groupValues?.get(1)?.toIntOrNull()
        val parsedMinutes = Regex("""(?i)(\d+)\s*(?:мин|minute)""").find(step.prescription)?.groupValues?.get(1)?.toIntOrNull()
        val parsedSeconds = Regex("""(?i)(\d+)\s*(?:сек|second)""").find(step.prescription)?.groupValues?.get(1)?.toIntOrNull()
        return step.copy(
            blockId = step.blockId.ifBlank { step.id },
            blockTitle = step.blockTitle.ifBlank { "Основной блок" },
            sets = if (step.sets > 1) step.sets else parsedSets ?: 1,
            reps = step.reps ?: parsedReps,
            workSeconds = step.workSeconds ?: parsedSeconds ?: parsedMinutes?.times(60),
        )
    }

    private fun compileStraight(steps: List<ExerciseStep>): List<WorkoutSetTarget> = buildList {
        steps.forEach { step ->
            val totalSets = step.sets.coerceAtLeast(1)
            repeat(totalSets) { index ->
                add(
                    target(
                        step = step,
                        roundIndex = 1,
                        setIndex = index + 1,
                        totalRounds = 1,
                        totalSets = totalSets,
                        rest = if (index < totalSets - 1) step.restSeconds else 0,
                    ),
                )
            }
        }
    }

    private fun compileRounds(steps: List<ExerciseStep>): List<WorkoutSetTarget> = buildList {
        val rounds = steps.maxOfOrNull { maxOf(it.rounds, it.sets) }?.coerceAtLeast(1) ?: 1
        repeat(rounds) { round ->
            steps.forEachIndexed { stepIndex, step ->
                val lastInRound = stepIndex == steps.lastIndex
                add(
                    target(
                        step = step,
                        roundIndex = round + 1,
                        setIndex = 1,
                        totalRounds = rounds,
                        totalSets = 1,
                        rest = if (lastInRound) step.restAfterRoundSeconds else step.restSeconds,
                    ),
                )
            }
        }
    }

    private fun target(
        step: ExerciseStep,
        roundIndex: Int,
        setIndex: Int,
        totalRounds: Int,
        totalSets: Int,
        rest: Int,
    ) = WorkoutSetTarget(
        step = step,
        blockId = step.blockId,
        blockTitle = step.blockTitle,
        blockType = step.blockType,
        roundIndex = roundIndex,
        setIndex = setIndex,
        totalRounds = totalRounds,
        totalSets = totalSets,
        plannedReps = step.reps,
        workSeconds = step.workSeconds,
        restAfterSeconds = rest,
    )

    private fun <T, K> List<T>.groupByPreservingOrder(key: (T) -> K): List<Pair<K, List<T>>> {
        val groups = linkedMapOf<K, MutableList<T>>()
        forEach { groups.getOrPut(key(it)) { mutableListOf() }.add(it) }
        return groups.map { it.key to it.value }
    }
}
