package ru.yakovenko.mountainform.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.GoalType
import ru.yakovenko.mountainform.domain.HeartRateBasis
import ru.yakovenko.mountainform.domain.HeartRateCompliance
import ru.yakovenko.mountainform.domain.HeartRateVerdict
import ru.yakovenko.mountainform.domain.RunningWeek
import ru.yakovenko.mountainform.ui.formatEpochDay
import ru.yakovenko.mountainform.ui.oneDecimal
import java.time.LocalDate
import kotlin.math.roundToInt

internal const val EASY_SHARE_GOAL_PERCENT = 80
private const val EASY_SHARE_WEEKS = 4
private const val UPCOMING_CHECKPOINTS = 3
private const val MIN_EASY_PACE_METERS = 2_000.0

internal data class HeartRateComplianceText(
    val headline: String,
    val detail: String,
    val needsAttention: Boolean,
)

internal fun heartRateComplianceText(compliance: HeartRateCompliance): HeartRateComplianceText {
    val average = compliance.averageHeartRate?.let { "средний ${it.roundToInt()}" }
    val headline = when (compliance.verdict) {
        HeartRateVerdict.WITHIN -> "Пульс в границе ${compliance.targetMax}"
        HeartRateVerdict.PARTLY_ABOVE -> "Пульс местами выше границы ${compliance.targetMax}"
        HeartRateVerdict.ABOVE -> "Пульс выше границы ${compliance.targetMax}"
    } + average?.let { " · $it" }.orEmpty()
    val detail = when (compliance.basis) {
        HeartRateBasis.SAMPLES -> buildString {
            append("Выше границы ${minutes(compliance.secondsAboveTarget)} из ${minutes(compliance.assessedSeconds)} мин")
            compliance.peakHeartRate?.let { append(" · пик $it") }
            append(" · по пульсу каждую секунду")
        }
        HeartRateBasis.LAPS -> buildString {
            append("Выше границы ${compliance.lapsAboveTarget} из ${compliance.lapsAssessed} кругов")
            append(" · около ${minutes(compliance.secondsAboveTarget)} из ${minutes(compliance.assessedSeconds)} мин")
            compliance.definiteSecondsAbove?.takeIf { it >= 60 }?.let {
                append(" · по зонам часов не меньше ${minutes(it)} мин")
            }
        }
        HeartRateBasis.AVERAGE -> "Оценка по среднему пульсу: кругов в записи нет"
    }
    return HeartRateComplianceText(headline, detail, compliance.verdict != HeartRateVerdict.WITHIN)
}

private fun minutes(seconds: Long): Long = (seconds + 30) / 60

/** Share of assessed running time at or below the easy ceiling over the latest weeks, in percent. */
internal fun recentEasySharePercent(weeks: List<RunningWeek>): Int? {
    val recent = weeks.takeLast(EASY_SHARE_WEEKS)
    val assessed = recent.sumOf { it.assessedSeconds }
    if (assessed <= 0) return null
    return (recent.sumOf { it.easySeconds } * 100.0 / assessed).roundToInt()
}

/** Pace of laps at or below the easy ceiling, seconds per kilometre; null below [MIN_EASY_PACE_METERS]. */
internal fun easyPaceSecondsPerKm(weeks: List<RunningWeek>): Int? {
    val meters = weeks.sumOf { it.easyPaceMeters }
    if (meters < MIN_EASY_PACE_METERS) return null
    return (weeks.sumOf { it.easyPaceSeconds } * 1000.0 / meters).roundToInt()
}

/** Latest four weeks against the four before them: the same heart rate at a quicker pace is progress. */
internal fun easyPaceTrendText(weeks: List<RunningWeek>, easyHeartRateMax: Int): String? {
    val recent = easyPaceSecondsPerKm(weeks.takeLast(EASY_SHARE_WEEKS))
    val earlier = easyPaceSecondsPerKm(weeks.dropLast(EASY_SHARE_WEEKS).takeLast(EASY_SHARE_WEEKS))
    return when {
        recent != null && earlier != null ->
            "Темп на пульсе до $easyHeartRateMax: ${paceText(recent)}/км · месяцем раньше ${paceText(earlier)}/км"
        recent != null -> "Темп на пульсе до $easyHeartRateMax: ${paceText(recent)}/км"
        else -> null
    }
}

private fun paceText(secondsPerKm: Int): String = "%d:%02d".format(secondsPerKm / 60, secondsPerKm % 60)

internal fun runCount(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "пробежек"
        count % 10 == 1 -> "пробежка"
        count % 10 in 2..4 -> "пробежки"
        else -> "пробежек"
    }
    return "$count $word"
}

internal fun daysUntil(days: Long): String = when {
    days == 0L -> "сегодня"
    days == 1L -> "завтра"
    else -> {
        val word = when {
            days % 100 in 11..14 -> "дней"
            days % 10 == 1L -> "день"
            days % 10 in 2..4 -> "дня"
            else -> "дней"
        }
        "через $days $word"
    }
}

private fun weekLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let { "%d.%02d".format(it.dayOfMonth, it.monthValue) }

private fun kilometres(meters: Double): String = (meters / 1000.0).oneDecimal().replace('.', ',')

@Composable
internal fun RunningWeeksCard(weeks: List<RunningWeek>, easyHeartRateMax: Int?) {
    val current = weeks.lastOrNull()
    val easyShare = recentEasySharePercent(weeks)
    val maxDistance = weeks.maxOfOrNull { it.distanceMeters } ?: 0.0
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Бег по неделям", fontWeight = FontWeight.Bold)
            if (current == null || maxDistance <= 0.0) {
                Text(
                    "Импортируйте пробежки из Garmin — здесь появится объём по неделям.",
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }
            Text(
                if (current.runs == 0) "На этой неделе пробежек пока нет"
                else "Эта неделя: ${kilometres(current.distanceMeters)} км · ${runCount(current.runs)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            RunningWeeksChart(weeks, maxDistance)
            when {
                easyShare != null && easyHeartRateMax != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(MaterialTheme.colorScheme.primary, "до $easyHeartRateMax")
                        Legend(MaterialTheme.colorScheme.tertiary, "выше")
                        Legend(MaterialTheme.colorScheme.outlineVariant, "без пульса")
                    }
                    Text(
                        "Лёгкого бега за $EASY_SHARE_WEEKS недели: $easyShare% при ориентире $EASY_SHARE_GOAL_PERCENT%. " +
                            "Оценка по кругам Garmin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (easyShare < EASY_SHARE_GOAL_PERCENT) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    easyPaceTrendText(weeks, easyHeartRateMax)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                else -> Text(
                    "Доля лёгкого бега появится, когда в плане будет граница пульса.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RunningWeeksChart(weeks: List<RunningWeek>, maxDistance: Double) {
    val description = weeks.joinToString("; ") { week ->
        "неделя с ${weekLabel(week.weekStartEpochDay)}: ${kilometres(week.distanceMeters)} км"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Бег по неделям. $description" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeks.forEach { week ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (week.distanceMeters > 0) (week.distanceMeters / 1000.0).roundToInt().toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(Modifier.fillMaxWidth().height(BAR_HEIGHT), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (week.distanceMeters / maxDistance).toFloat().coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Column(
                            Modifier.fillMaxWidth().height(BAR_HEIGHT * fraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        ) {
                            val easy = week.easyShare?.toFloat()
                            if (easy == null) {
                                Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
                            } else {
                                if (easy < 1f) {
                                    Box(Modifier.fillMaxWidth().weight(1f - easy).background(MaterialTheme.colorScheme.tertiary))
                                }
                                if (easy > 0f) {
                                    Box(Modifier.fillMaxWidth().weight(easy).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }
                }
                Text(
                    weekLabel(week.weekStartEpochDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private val BAR_HEIGHT = 88.dp

@Composable
private fun Legend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

internal fun upcomingCheckpoints(goals: List<GoalEventEntity>, todayEpochDay: Long): List<GoalEventEntity> = goals
    .filter { it.type == GoalType.CHECKPOINT && (it.targetEpochDay ?: Long.MIN_VALUE) >= todayEpochDay }
    .sortedBy { it.targetEpochDay }
    .take(UPCOMING_CHECKPOINTS)

/** The headline of a goal note: its first fragment, e.g. «Цель 1:45». */
internal fun goalHeadline(notes: String): String = notes.substringBefore(" · ").trim()

@Composable
internal fun SeasonCard(goals: List<GoalEventEntity>, todayEpochDay: Long) {
    val targets = goals.filter { it.type == GoalType.RUNNING || it.type == GoalType.MOUNTAIN }
    val checkpoints = upcomingCheckpoints(goals, todayEpochDay)
    if (targets.isEmpty() && checkpoints.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Цели сезона", fontWeight = FontWeight.Bold)
            targets.forEach { goal -> SeasonEntry(goal, todayEpochDay, expanded) }
            if (checkpoints.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    if (expanded) "Контрольные точки" else "Ближайшая контрольная точка",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                (if (expanded) checkpoints else checkpoints.take(1)).forEach { SeasonEntry(it, todayEpochDay, expanded) }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("season_details_toggle")) {
                Text(if (expanded) "Свернуть" else "Подробнее")
            }
        }
    }
}

@Composable
private fun SeasonEntry(goal: GoalEventEntity, todayEpochDay: Long, expanded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(goal.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        goal.targetEpochDay?.let { day ->
            val distance = day - todayEpochDay
            Text(
                formatEpochDay(day) + if (distance >= 0) " · ${daysUntil(distance)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val notes = if (expanded || goal.type == GoalType.CHECKPOINT) goal.notes else goalHeadline(goal.notes)
        if (notes.isNotBlank() && (expanded || goal.type != GoalType.CHECKPOINT)) {
            Text(notes, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
