package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.data.catalogId
import ru.yakovenko.mountainform.data.targetLabel
import ru.yakovenko.mountainform.domain.WorkoutSetTarget

private const val VISIBLE_KEY_POINTS = 2

/** A session objective as separate short statements: one per sentence. */
internal fun objectivePoints(objective: String): List<String> = objective
    .split(Regex("""(?<=[.!?])\s+"""))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

/** The planned intensity of the session: the target of its longest step with one, such as the main easy run. */
internal fun sessionTargetLabel(steps: List<ExerciseStep>): String? =
    steps.filter { it.targetLabel() != null }.maxByOrNull { it.workSeconds ?: 0 }?.targetLabel()

/** The dose of a step for a one-line plan row; the intensity target is added unless the dose already names it. */
internal fun planRowDose(step: ExerciseStep): String {
    val dose = step.prescription.lowercase()
    val target = step.targetLabel()?.takeIf { "пульс" !in dose && "темп" !in dose }
    return listOfNotNull(step.prescription.takeIf { it.isNotBlank() }, target).joinToString(" · ")
}

internal data class PlanBlock(val title: String?, val steps: List<ExerciseStep>)

/** Blocks in plan order. A block gets a heading only when it groups several exercises. */
internal fun planBlocks(targets: List<WorkoutSetTarget>): List<PlanBlock> {
    val blocks = linkedMapOf<String, MutableList<WorkoutSetTarget>>()
    targets.forEach { blocks.getOrPut(it.blockId) { mutableListOf() }.add(it) }
    return blocks.values.map { blockTargets ->
        val steps = blockTargets.map { it.step }.distinctBy { it.id }
        PlanBlock(blockTargets.first().blockTitle.takeIf { steps.size > 1 }, steps)
    }
}

private fun stepIcon(step: ExerciseStep): ImageVector = when (step.catalogId()) {
    "walk", "aerobic" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "run-walk", "stride", "stride-recovery" -> Icons.AutoMirrored.Filled.DirectionsRun
    "mobility", "breathing" -> Icons.Default.SelfImprovement
    else -> Icons.Default.FitnessCenter
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionFacts(facts: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        facts.forEach { fact ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(100.dp)) {
                Text(
                    fact,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun SessionPlanCard(objective: String, targets: List<WorkoutSetTarget>) {
    val points = remember(objective) { objectivePoints(objective) }
    var showAllPoints by remember(objective) { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("План тренировки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (points.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (if (showAllPoints) points else points.take(VISIBLE_KEY_POINTS)).forEach { point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Text(point, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                    if (points.size > VISIBLE_KEY_POINTS) {
                        TextButton(
                            onClick = { showAllPoints = !showAllPoints },
                            modifier = Modifier.testTag("plan_points_toggle"),
                        ) { Text(if (showAllPoints) "Свернуть" else "Ещё ${points.size - VISIBLE_KEY_POINTS}") }
                    }
                }
            }
            planBlocks(targets).forEach { block ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.title?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    block.steps.forEach { step -> PlanStepRow(step) }
                }
            }
        }
    }
}

@Composable
private fun PlanStepRow(step: ExerciseStep, dose: String = planRowDose(step), muted: Boolean = false) {
    val illustration = remember(step) { stepIllustration(step) }
    var zoomed by remember(step) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().testTag("plan_step_${step.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (illustration != null) {
            Image(
                painter = painterResource(illustration),
                contentDescription = "Схема: ${step.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 76.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable { zoomed = true }
                    .testTag("plan_thumb_${step.id}"),
            )
        } else {
            Box(
                Modifier.size(width = 76.dp, height = 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        stepIcon(step),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (zoomed && illustration != null) IllustrationZoomDialog(illustration) { zoomed = false }
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge)
            dose.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What was actually done in a step of a finished workout, from its recorded sets. */
internal fun doneDose(stepTargets: List<WorkoutSetTarget>, logs: List<SessionSetLogEntity>): String {
    val step = stepTargets.first().step
    val done = logs.filter { it.stepId == step.id && it.completed }.distinctBy { it.roundIndex to it.setIndex }
    if (done.isEmpty()) return "не отмечено"
    val planned = stepTargets.size
    val reps = done.mapNotNull { it.actualReps ?: it.plannedReps }
    val repsText = when {
        reps.isEmpty() -> stepTargets.first().workSeconds?.let { seconds ->
            if (seconds % 60 == 0) "${seconds / 60} мин" else "$seconds сек"
        }
        reps.distinct().size == 1 -> "по ${reps.first()}"
        else -> reps.joinToString(", ")
    }
    val load = done.mapNotNull { it.loadKg }.maxOrNull()?.let { kg ->
        if (kg % 1.0 == 0.0) "${kg.toInt()} кг" else "$kg кг"
    }
    val count = if (planned > 1) "${done.size} из $planned" else null
    return listOfNotNull(count, repsText, load).joinToString(" · ").ifEmpty { "выполнено" }
}

/** The plan of a finished workout with what was recorded for each exercise. */
@Composable
internal fun CompletedSessionCard(targets: List<WorkoutSetTarget>, logs: List<SessionSetLogEntity>) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Что сделано", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            planBlocks(targets).forEach { block ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.title?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    block.steps.forEach { step ->
                        val dose = doneDose(targets.filter { it.step.id == step.id }, logs)
                        PlanStepRow(step, dose = dose, muted = dose == "не отмечено")
                    }
                }
            }
        }
    }
}
