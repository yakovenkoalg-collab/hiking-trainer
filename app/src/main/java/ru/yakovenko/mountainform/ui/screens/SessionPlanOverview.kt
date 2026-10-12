package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.background
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
import ru.yakovenko.mountainform.data.catalogId
import ru.yakovenko.mountainform.data.targetLabel
import ru.yakovenko.mountainform.domain.WorkoutSetTarget

private const val VISIBLE_KEY_POINTS = 2

/** A session objective as separate short statements: one per sentence. */
internal fun objectivePoints(objective: String): List<String> = objective
    .split(Regex("""(?<=[.!?])\s+"""))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

/** The planned intensity of the session, when all its heart-rate and pace targets agree. */
internal fun sessionTargetLabel(steps: List<ExerciseStep>): String? =
    steps.mapNotNull { it.targetLabel() }.distinct().singleOrNull()

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
private fun PlanStepRow(step: ExerciseStep) {
    Row(
        Modifier.fillMaxWidth().testTag("plan_step_${step.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge)
            planRowDose(step).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
