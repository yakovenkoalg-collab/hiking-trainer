package ru.yakovenko.mountainform.domain

import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.data.SetTimingStatus
import java.time.LocalDate

data class SessionCompletionDetails(
    val performedEpochDay: Long = LocalDate.now().toEpochDay(),
    val recordingMode: String = "LIVE",
    val retrospectiveLogs: List<SessionSetLogEntity> = emptyList(),
)

fun WorkoutSetTarget.doseLabel(): String {
    val eachSide = step.prescription.contains("на ногу", true) || step.prescription.contains("на сторону", true)
    val dose = when {
        workSeconds != null && workSeconds % 60 == 0 -> "${workSeconds / 60} мин"
        workSeconds != null -> "$workSeconds сек"
        plannedReps != null -> "$plannedReps повторений"
        else -> step.prescription
    }
    val suffix = step.prescription.substringAfter('·', "").trim()
    return dose + (if (eachSide) " на каждую сторону" else "") + (if (suffix.isNotEmpty()) " · $suffix" else "")
}

/** A retrospective check mark records completion, never elapsed work or rest. */
fun retrospectiveSetLog(sessionId: String, target: WorkoutSetTarget, loggedAt: Long) = SessionSetLogEntity(
    sessionId = sessionId,
    stepId = target.step.id,
    roundIndex = target.roundIndex,
    setIndex = target.setIndex,
    plannedReps = target.plannedReps,
    // The user confirms completion, not an unedited planned repetition count.
    actualReps = null,
    completedAtEpochMillis = loggedAt,
    timingStatus = SetTimingStatus.NOT_USED,
    plannedRestSeconds = target.restAfterSeconds,
    completed = true,
)
