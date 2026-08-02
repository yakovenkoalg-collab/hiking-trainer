package ru.yakovenko.mountainform.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.R
import ru.yakovenko.mountainform.data.ExerciseCatalogEntity
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.data.SessionStepLogEntity
import ru.yakovenko.mountainform.data.SetTimingStatus
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.catalogId
import ru.yakovenko.mountainform.data.imageKey
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.formatEpochDay
import ru.yakovenko.mountainform.domain.WorkoutPlanCompiler
import ru.yakovenko.mountainform.domain.WorkoutExecutionState
import ru.yakovenko.mountainform.domain.WorkoutSetTarget
import ru.yakovenko.mountainform.domain.WorkoutTimerMode

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SessionScreen(
    padding: PaddingValues,
    session: TrainingSessionEntity?,
    steps: List<ExerciseStep>,
    catalog: List<ExerciseCatalogEntity>,
    stepLogs: List<SessionStepLogEntity>,
    setLogs: List<SessionSetLogEntity>,
    shoulderRestrictionActive: Boolean,
    loadBlocked: Boolean,
    adaptationRequired: Boolean,
    readinessRecommendation: String,
    readinessReasons: List<String>,
    initialExecutionState: WorkoutExecutionState?,
    onExecutionStateChanged: (WorkoutExecutionState) -> Unit,
    onStepCompleted: (String, String, Boolean) -> Unit,
    onSaveSetLog: (SessionSetLogEntity) -> Unit,
    onBack: () -> Unit,
    onEditReadiness: () -> Unit,
    onTimerFinished: (WorkoutTimerMode) -> Unit = {},
    onComplete: (String, Int, String, Int) -> Unit,
    onSkip: (String, String) -> Unit,
) {
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("Тренировка не найдена")
            TextButton(onClick = onBack) { Text("Назад") }
        }
        return
    }

    val targets = remember(steps) { WorkoutPlanCompiler.compile(steps) }
    val sessionSetLogs = setLogs.filter { it.sessionId == session.id && it.completed }
    fun key(target: WorkoutSetTarget) = listOf(target.step.id, target.roundIndex, target.setIndex)
    val completedTargetKeys = sessionSetLogs.mapTo(mutableSetOf()) { listOf(it.stepId, it.roundIndex, it.setIndex) }
    val initialTarget = targets.indexOfFirst { key(it) !in completedTargetKeys }.takeIf { it >= 0 } ?: 0
    val restoredTarget = initialExecutionState?.targetIndex?.coerceIn(0, targets.lastIndex.coerceAtLeast(0)) ?: initialTarget
    var executionState by remember(session.id) {
        mutableStateOf(
            initialExecutionState?.copy(targetIndex = restoredTarget)
                ?: WorkoutExecutionState(
                    sessionId = session.id,
                    targetIndex = restoredTarget,
                    workRemainingSeconds = targets.getOrNull(restoredTarget)?.workSeconds ?: 0,
                ),
        )
    }
    var clockEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCompletion by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }
    var showSkipStage by remember { mutableStateOf(false) }
    var showPainStop by remember { mutableStateOf(false) }
    var showSetResult by remember { mutableStateOf(false) }
    var editResultOnly by remember { mutableStateOf(false) }
    var showResetTimerConfirm by remember { mutableStateOf(false) }
    var showOverview by remember(session.id) {
        mutableStateOf(session.status == SessionStatus.PLANNED && initialExecutionState?.workoutStarted != true)
    }
    var pendingRestLog by remember(session.id) { mutableStateOf<SessionSetLogEntity?>(null) }
    val targetIndex = executionState.targetIndex
    val workoutSeconds = executionState.workoutElapsedAt(clockEpochMillis)
    val setSeconds = executionState.setElapsedAt(clockEpochMillis)
    val workRemaining = executionState.workRemainingAt(clockEpochMillis)
    val restSeconds = executionState.restRemainingAt(clockEpochMillis)
    val restElapsedSeconds = executionState.restElapsedAt(clockEpochMillis)
    val restOvertimeSeconds = executionState.restOvertimeAt(clockEpochMillis)
    val setRunning = executionState.timerMode == WorkoutTimerMode.SET && !executionState.paused
    val paused = executionState.paused
    val target = targets.getOrNull(targetIndex)
    val step = target?.step
    val details = step?.let { current -> catalog.firstOrNull { it.id == current.catalogId() } }
    val completedCount = targets.count { key(it) in completedTargetKeys }
    val currentLog = target?.let { current ->
        sessionSetLogs.firstOrNull {
            it.stepId == current.step.id && it.roundIndex == current.roundIndex && it.setIndex == current.setIndex
        }
    }

    fun persistExecution(next: WorkoutExecutionState) {
        executionState = next
        clockEpochMillis = System.currentTimeMillis()
        onExecutionStateChanged(next)
    }

    fun selectTarget(index: Int, restAfterSeconds: Int = 0, restSourceTargetIndex: Int? = null) {
        val now = System.currentTimeMillis()
        val nextIndex = index.coerceIn(0, targets.lastIndex.coerceAtLeast(0))
        val nextTimer = if (restAfterSeconds > 0) WorkoutTimerMode.REST else WorkoutTimerMode.NONE
        persistExecution(
            executionState.snapshotAt(now).copy(
                targetIndex = nextIndex,
                timerMode = nextTimer,
                timerTickStartedAtEpochMillis = if (nextTimer == WorkoutTimerMode.REST && !paused) now else null,
                setElapsedSeconds = 0,
                workRemainingSeconds = targets.getOrNull(nextIndex)?.workSeconds ?: 0,
                restRemainingSeconds = restAfterSeconds,
                restElapsedSeconds = 0,
                restPlannedSeconds = restAfterSeconds,
                restSourceTargetIndex = restSourceTargetIndex,
                restAlerted = false,
            ),
        )
    }

    fun finishRest(skipped: Boolean) {
        val now = System.currentTimeMillis()
        val snapshot = executionState.snapshotAt(now)
        val source = targets.getOrNull(snapshot.restSourceTargetIndex ?: -1)
        val stored = source?.let { sourceTarget ->
            sessionSetLogs.firstOrNull {
                it.stepId == sourceTarget.step.id &&
                    it.roundIndex == sourceTarget.roundIndex &&
                    it.setIndex == sourceTarget.setIndex
            }
        }
        (pendingRestLog ?: stored)?.let { log ->
            onSaveSetLog(
                log.copy(
                    actualRestSeconds = snapshot.restElapsedSeconds,
                    restSkipped = skipped,
                ),
            )
        }
        pendingRestLog = null
        persistExecution(
            snapshot.copy(
                timerMode = WorkoutTimerMode.NONE,
                timerTickStartedAtEpochMillis = null,
                restRemainingSeconds = 0,
                restElapsedSeconds = 0,
                restPlannedSeconds = 0,
                restSourceTargetIndex = null,
                restAlerted = false,
            ),
        )
    }

    fun saveTargetResult(
        resultTarget: WorkoutSetTarget,
        reps: Int?,
        load: Double?,
        rpe: Int?,
        rir: Int?,
        pain: Boolean,
        painNote: String,
        advance: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val snapshot = executionState.snapshotAt(now)
        val existing = sessionSetLogs.firstOrNull {
            it.stepId == resultTarget.step.id &&
                it.roundIndex == resultTarget.roundIndex &&
                it.setIndex == resultTarget.setIndex
        }
        val loggedElapsed = if (advance) snapshot.setElapsedSeconds else existing?.elapsedSeconds ?: snapshot.setElapsedSeconds
        val timingStatus = if (loggedElapsed > 0) SetTimingStatus.RECORDED else SetTimingStatus.NOT_USED
        val log = SessionSetLogEntity(
            sessionId = session.id,
            stepId = resultTarget.step.id,
            roundIndex = resultTarget.roundIndex,
            setIndex = resultTarget.setIndex,
            plannedReps = resultTarget.plannedReps,
            actualReps = reps,
            loadKg = load,
            actualRpe = rpe,
            rir = rir,
            pain = pain,
            painNote = painNote,
            startedAtEpochMillis = now - loggedElapsed * 1_000L,
            completedAtEpochMillis = now,
            elapsedSeconds = loggedElapsed,
            timingStatus = timingStatus,
            plannedRestSeconds = resultTarget.restAfterSeconds,
            actualRestSeconds = existing?.actualRestSeconds,
            restSkipped = existing?.restSkipped ?: false,
            completed = true,
        )
        onSaveSetLog(log)
        val otherTargetsForStepDone = targets
            .filter { it.step.id == resultTarget.step.id && key(it) != key(resultTarget) }
            .all { key(it) in completedTargetKeys }
        if (otherTargetsForStepDone) onStepCompleted(session.id, resultTarget.step.id, true)
        if (!advance) return
        if (pain) {
            if (snapshot.workoutStarted && !snapshot.paused) {
                persistExecution(
                    snapshot.copy(
                        paused = true,
                        workoutTickStartedAtEpochMillis = null,
                        timerTickStartedAtEpochMillis = null,
                    ),
                )
            }
            showPainStop = true
            return
        }
        if (targetIndex < targets.lastIndex) {
            if (resultTarget.restAfterSeconds > 0) pendingRestLog = log
            selectTarget(
                index = targetIndex + 1,
                restAfterSeconds = resultTarget.restAfterSeconds,
                restSourceTargetIndex = targetIndex.takeIf { resultTarget.restAfterSeconds > 0 },
            )
        } else {
            persistExecution(
                snapshot.copy(
                    timerMode = WorkoutTimerMode.NONE,
                    timerTickStartedAtEpochMillis = null,
                    setElapsedSeconds = 0,
                    workRemainingSeconds = 0,
                ),
            )
            showCompletion = true
        }
    }

    fun quickCompleteCurrentTarget() {
        val current = target ?: return
        val previousForExercise = sessionSetLogs
            .filter { it.stepId == current.step.id && key(current) != listOf(it.stepId, it.roundIndex, it.setIndex) }
            .maxByOrNull { it.completedAtEpochMillis ?: 0L }
        saveTargetResult(
            resultTarget = current,
            reps = current.plannedReps ?: previousForExercise?.actualReps,
            load = previousForExercise?.loadKg,
            rpe = null,
            rir = null,
            pain = false,
            painNote = "",
            advance = true,
        )
    }

    fun pauseWorkoutForSafety() {
        if (!executionState.workoutStarted || executionState.paused) return
        val now = System.currentTimeMillis()
        persistExecution(
            executionState.snapshotAt(now).copy(
                paused = true,
                workoutTickStartedAtEpochMillis = null,
                timerTickStartedAtEpochMillis = null,
            ),
        )
    }

    LaunchedEffect(
        session.id,
        executionState.workoutStarted,
        executionState.paused,
        executionState.timerMode,
        executionState.timerTickStartedAtEpochMillis,
    ) {
        while (session.status == SessionStatus.PLANNED && executionState.workoutStarted && !executionState.paused) {
            clockEpochMillis = System.currentTimeMillis()
            delay(250)
        }
    }
    LaunchedEffect(workRemaining, restSeconds, executionState.timerMode, executionState.restAlerted) {
        if (target?.workSeconds != null && workRemaining == 0 && executionState.timerMode == WorkoutTimerMode.SET) {
            onTimerFinished(WorkoutTimerMode.SET)
            quickCompleteCurrentTarget()
        } else if (
            restSeconds == 0 &&
            executionState.timerMode == WorkoutTimerMode.REST &&
            !executionState.restAlerted
        ) {
            val now = System.currentTimeMillis()
            onTimerFinished(WorkoutTimerMode.REST)
            persistExecution(executionState.snapshotAt(now).copy(restAlerted = true, timerTickStartedAtEpochMillis = now))
        }
    }
    LaunchedEffect(loadBlocked, executionState.workoutStarted, executionState.paused) {
        if (loadBlocked && executionState.workoutStarted && !executionState.paused) {
            pauseWorkoutForSafety()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("session_content"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatEpochDay(session.plannedEpochDay), color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (executionState.workoutStarted) {
                                "${formatDuration(workoutSeconds)} · RPE ${session.targetRpe}"
                            } else {
                                "Не начата · RPE ${session.targetRpe}"
                            },
                        )
                    }
                    Text(session.objective, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { if (targets.isEmpty()) 0f else completedCount.toFloat() / targets.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Упражнений: ${steps.size} · этапов: ${targets.size}", style = MaterialTheme.typography.labelMedium)
                    Text("Этапов выполнено: $completedCount из ${targets.size}", style = MaterialTheme.typography.labelMedium)
                    if (session.status == SessionStatus.PLANNED && !executionState.workoutStarted) {
                        Text(
                            "Сначала посмотрите весь план. Таймер отдельного упражнения запускается только по вашей команде.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (session.status == SessionStatus.PLANNED) {
                        OutlinedButton(
                            onClick = { showOverview = !showOverview },
                            modifier = Modifier.fillMaxWidth().testTag("workout_overview_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            Text(if (showOverview) "  Вернуться к тренировке" else "  Весь план")
                        }
                        OutlinedButton(
                            onClick = {
                                val now = System.currentTimeMillis()
                                val snapshot = executionState.snapshotAt(now)
                                persistExecution(
                                    if (paused) {
                                        snapshot.copy(
                                            paused = false,
                                            workoutTickStartedAtEpochMillis = now,
                                            timerTickStartedAtEpochMillis = if (snapshot.timerMode == WorkoutTimerMode.NONE) null else now,
                                        )
                                    } else {
                                        snapshot.copy(
                                            paused = true,
                                            workoutTickStartedAtEpochMillis = null,
                                            timerTickStartedAtEpochMillis = null,
                                        )
                                    },
                                )
                            },
                            enabled = !(paused && loadBlocked),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                            Text(if (paused) "  Продолжить тренировку" else "  Пауза")
                        }
                    }
                }
            }

            if (shoulderRestrictionActive) {
                item {
                    SafetyBanner("Плечо: только безболезненный диапазон. Не добавляйте подтягивания, брусья и движения над головой.")
                }
            }
            if (loadBlocked) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SafetyBanner(
                            "Тренировочная нагрузка заблокирована по сегодняшней оценке. " +
                                if (readinessReasons.isEmpty()) {
                                    readinessRecommendation
                                } else {
                                    "Причина: ${readinessReasons.joinToString("; ")}. $readinessRecommendation"
                                },
                        )
                        OutlinedButton(
                            onClick = onEditReadiness,
                            modifier = Modifier.fillMaxWidth().testTag("edit_readiness_button"),
                        ) {
                            Text("Изменить сегодняшнюю оценку")
                        }
                    }
                }
            } else if (adaptationRequired) {
                item {
                    SafetyBanner("Сегодня нужна адаптация. $readinessRecommendation Приложение не заменяет упражнение автоматически.")
                }
            }

            if (showOverview && session.status == SessionStatus.PLANNED) {
                item {
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Обзор тренировки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("${session.durationMinutes} мин · целевой RPE ${session.targetRpe}")
                            Text(session.objective)
                            targets.groupBy { it.blockId }.values.forEach { blockTargets ->
                                val first = blockTargets.first()
                                Text(
                                    first.blockTitle + " · " + blockTypeLabel(first.blockType),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                blockTargets.distinctBy { it.step.id }.forEach { blockTarget ->
                                    val count = blockTargets.count { it.step.id == blockTarget.step.id }
                                    Text(
                                        "• ${blockTarget.step.title} — ${blockTarget.step.prescription}" +
                                            if (count > 1) " · $count этапа" else "",
                                    )
                                }
                                blockTargets.maxOfOrNull { it.restAfterSeconds }
                                    ?.takeIf { it > 0 }
                                    ?.let { Text("Отдых: до $it сек") }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (!executionState.workoutStarted) {
                                val now = System.currentTimeMillis()
                                persistExecution(
                                    executionState.snapshotAt(now).copy(
                                        workoutStarted = true,
                                        paused = false,
                                        workoutTickStartedAtEpochMillis = now,
                                    ),
                                )
                            }
                            showOverview = false
                        },
                        enabled = !loadBlocked,
                        modifier = Modifier.fillMaxWidth().testTag("start_workout_button"),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(if (executionState.workoutStarted) "  Продолжить тренировку" else "  Начать тренировку")
                    }
                }
            }

            if (!showOverview) target?.let { currentTarget ->
                val current = currentTarget.step
                item {
                    Text(
                        currentTarget.blockTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            currentTarget.totalRounds > 1 -> "Круг ${currentTarget.roundIndex} из ${currentTarget.totalRounds} · ${blockTypeLabel(currentTarget.blockType)}"
                            currentTarget.totalSets > 1 -> "Подход ${currentTarget.setIndex} из ${currentTarget.totalSets} · ${blockTypeLabel(currentTarget.blockType)}"
                            else -> "Этап ${targetIndex + 1} из ${targets.size} · ${blockTypeLabel(currentTarget.blockType)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(current.prescription, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (executionState.timerMode == WorkoutTimerMode.REST) {
                    item {
                        Card {
                            Column(
                                Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(if (restSeconds > 0) "Отдых" else "Отдых завершён", fontWeight = FontWeight.Bold)
                                Text(
                                    if (restSeconds > 0) "$restSeconds сек" else "+${formatDuration(restOvertimeSeconds)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (restSeconds == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text("Прошло: ${formatDuration(restElapsedSeconds)} · план: ${formatDuration(executionState.restPlannedSeconds)}")
                                Button(
                                    onClick = { finishRest(skipped = restSeconds > 0) },
                                    modifier = Modifier.fillMaxWidth().testTag("finish_rest_button"),
                                ) {
                                    Text(if (restSeconds > 0) "Завершить отдых сейчас" else "Перейти к следующему этапу")
                                }
                            }
                        }
                    }
                }
                if (session.status == SessionStatus.PLANNED && executionState.timerMode != WorkoutTimerMode.REST) {
                    item {
                        Card {
                            Column(
                                Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(if (currentTarget.workSeconds != null) "Таймер упражнения" else "Секундомер подхода", fontWeight = FontWeight.Bold)
                                Text(
                                    if (currentTarget.workSeconds != null) formatDuration(workRemaining) else formatDuration(setSeconds),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                if (currentLog != null) {
                                    Text(
                                        buildString {
                                            append("Записано")
                                            currentLog.actualReps?.let { append(" · $it повт.") }
                                            currentLog.loadKg?.let { append(" · $it кг") }
                                            currentLog.actualRpe?.let { append(" · RPE $it") }
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            editResultOnly = true
                                            showSetResult = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Изменить запись")
                                    }
                                } else {
                                    val timerUnit = if (currentTarget.totalSets > 1) "подход" else "этап"
                                    OutlinedButton(
                                        onClick = {
                                            val now = System.currentTimeMillis()
                                            val snapshot = executionState.snapshotAt(now)
                                            persistExecution(
                                                if (setRunning) {
                                                    snapshot.copy(timerMode = WorkoutTimerMode.NONE, timerTickStartedAtEpochMillis = null)
                                                } else {
                                                    val restartTimedSet = currentTarget.workSeconds != null && snapshot.workRemainingSeconds == 0
                                                    snapshot.copy(
                                                        workoutStarted = true,
                                                        paused = false,
                                                        workoutTickStartedAtEpochMillis =
                                                            if (snapshot.workoutStarted) snapshot.workoutTickStartedAtEpochMillis else now,
                                                        timerMode = WorkoutTimerMode.SET,
                                                        timerTickStartedAtEpochMillis = now,
                                                        setElapsedSeconds = if (restartTimedSet) 0 else snapshot.setElapsedSeconds,
                                                        workRemainingSeconds = if (restartTimedSet) currentTarget.workSeconds else snapshot.workRemainingSeconds,
                                                    )
                                                },
                                            )
                                        },
                                        enabled = !loadBlocked && !paused,
                                        modifier = Modifier.fillMaxWidth().testTag("set_timer_button"),
                                    ) {
                                        Icon(if (setRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                                        Text(
                                            when {
                                                setRunning -> "  Приостановить $timerUnit"
                                                else -> "  Начать $timerUnit"
                                            },
                                        )
                                    }
                                    val timerHasProgress = currentTarget.workSeconds?.let { workRemaining < it } ?: (setSeconds > 0)
                                    if (timerHasProgress) {
                                        TextButton(
                                            onClick = { showResetTimerConfirm = true },
                                            modifier = Modifier.fillMaxWidth().testTag("reset_set_timer_button"),
                                        ) {
                                            Text("Сбросить таймер упражнения")
                                        }
                                    }
                                    Button(
                                        onClick = { quickCompleteCurrentTarget() },
                                        enabled = executionState.workoutStarted && !loadBlocked && !paused,
                                        modifier = Modifier.fillMaxWidth().testTag("complete_set_button"),
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Text(
                                            if (setSeconds == 0) "  Готово без таймера" else "  Готово · ${formatDuration(setSeconds)}",
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            editResultOnly = false
                                            val now = System.currentTimeMillis()
                                            persistExecution(
                                                executionState.snapshotAt(now).copy(
                                                    timerMode = WorkoutTimerMode.NONE,
                                                    timerTickStartedAtEpochMillis = null,
                                                ),
                                            )
                                            showSetResult = true
                                        },
                                        enabled = executionState.workoutStarted && !loadBlocked && !paused,
                                        modifier = Modifier.fillMaxWidth().testTag("detailed_set_result_button"),
                                    ) {
                                        Text("Записать подробнее")
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { if (targetIndex > 0) selectTarget(targetIndex - 1) },
                                enabled = targetIndex > 0,
                                modifier = Modifier.weight(1f),
                            ) { Text("Назад") }
                            OutlinedButton(
                                onClick = {
                                    if (currentLog == null) {
                                        showSkipStage = true
                                    } else if (targetIndex < targets.lastIndex) {
                                        selectTarget(targetIndex + 1)
                                    } else {
                                        showCompletion = true
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("next_stage_button"),
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                                Text(if (targetIndex == targets.lastIndex) "  Итог" else if (currentLog == null) "  Пропустить" else "  Далее")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                pauseWorkoutForSafety()
                                showPainStop = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Возникла боль — остановиться")
                        }
                    }
                }
                item {
                    Text(
                        "Техника выполнения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item { ExerciseIllustration(current.imageKey()) }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            details?.setup?.takeIf { it.isNotBlank() }?.let { Instruction("Подготовка", it) }
                            Instruction("Выполнение", details?.execution ?: current.instructions)
                            details?.breathing?.takeIf { it.isNotBlank() }?.let { Instruction("Дыхание", it) }
                            if (details == null && current.instructions.isNotBlank()) {
                                Text(current.instructions)
                            }
                            if (current.restSeconds > 0) Text("Отдых: ${current.restSeconds} сек", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                details?.let { catalogItem ->
                    item {
                        val mistakes = remember(catalogItem.commonMistakesJson) {
                            runCatching { Json.decodeFromString<List<String>>(catalogItem.commonMistakesJson) }.getOrDefault(emptyList())
                        }
                        if (mistakes.isNotEmpty()) {
                            Card {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Частые ошибки", fontWeight = FontWeight.Bold)
                                    mistakes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            }

            if (session.status != SessionStatus.PLANNED) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (session.status == SessionStatus.COMPLETED) "Тренировка выполнена" else "Тренировка остановлена или пропущена",
                                fontWeight = FontWeight.Bold,
                            )
                            session.actualRpe?.let { Text("Фактический RPE: $it") }
                            if (session.completionNotes.isNotBlank()) Text(session.completionNotes)
                        }
                    }
                }
            } else {
                item {
                    TextButton(onClick = { showSkip = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Пропустить тренировку")
                    }
                }
            }
        }
    }

    if (showCompletion) {
        CompletionDialog(
            initialRpe = session.targetRpe,
            actualDurationSeconds = workoutSeconds,
            onDismiss = { showCompletion = false },
            onConfirm = { rpe, notes -> onComplete(session.id, rpe, notes, workoutSeconds) },
        )
    }
    if (showSetResult && target != null) {
        SetResultDialog(
            target = target,
            elapsedSeconds = setSeconds,
            initialRpe = session.targetRpe,
            existing = currentLog,
            onDismiss = { showSetResult = false },
            onConfirm = { reps, load, rpe, rir, pain, painNote ->
                saveTargetResult(
                    resultTarget = target,
                    reps = reps,
                    load = load,
                    rpe = rpe,
                    rir = rir,
                    pain = pain,
                    painNote = painNote,
                    advance = !editResultOnly,
                )
                showSetResult = false
                editResultOnly = false
            },
        )
    }
    if (showSkip) {
        NotesDialog(
            title = "Почему тренировка пропущена?",
            confirmLabel = "Сохранить",
            onDismiss = { showSkip = false },
            onConfirm = { onSkip(session.id, it) },
        )
    }
    if (showSkipStage && target != null) {
        NotesDialog(
            title = "Почему пропускаете этап?",
            confirmLabel = "Пропустить этап",
            onDismiss = { showSkipStage = false },
            onConfirm = { reason ->
                val now = System.currentTimeMillis()
                val snapshot = executionState.snapshotAt(now)
                onSaveSetLog(
                    SessionSetLogEntity(
                        sessionId = session.id,
                        stepId = target.step.id,
                        roundIndex = target.roundIndex,
                        setIndex = target.setIndex,
                        plannedReps = target.plannedReps,
                        actualReps = null,
                        loadKg = null,
                        actualRpe = null,
                        rir = null,
                        pain = false,
                        painNote = "Пропуск этапа: ${reason.ifBlank { "без причины" }}",
                        startedAtEpochMillis = now - snapshot.setElapsedSeconds * 1_000L,
                        completedAtEpochMillis = now,
                        elapsedSeconds = snapshot.setElapsedSeconds,
                        timingStatus = if (snapshot.setElapsedSeconds > 0) SetTimingStatus.RECORDED else SetTimingStatus.NOT_USED,
                        plannedRestSeconds = target.restAfterSeconds,
                        completed = false,
                    ),
                )
                persistExecution(
                    snapshot.copy(
                        timerMode = WorkoutTimerMode.NONE,
                        timerTickStartedAtEpochMillis = null,
                    ),
                )
                showSkipStage = false
                if (targetIndex < targets.lastIndex) selectTarget(targetIndex + 1) else showCompletion = true
            },
        )
    }
    if (showResetTimerConfirm && target != null) {
        AlertDialog(
            onDismissRequest = { showResetTimerConfirm = false },
            title = { Text("Сбросить таймер этапа?") },
            text = { Text("Текущее время этого этапа будет обнулено. Общий таймер тренировки сохранится.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        persistExecution(
                            executionState.snapshotAt(now).copy(
                                timerMode = WorkoutTimerMode.NONE,
                                timerTickStartedAtEpochMillis = null,
                                setElapsedSeconds = 0,
                                workRemainingSeconds = target.workSeconds ?: 0,
                            ),
                        )
                        showResetTimerConfirm = false
                    },
                ) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { showResetTimerConfirm = false }) { Text("Отмена") } },
        )
    }
    if (showPainStop) {
        AlertDialog(
            onDismissRequest = { showPainStop = false },
            title = { Text("Остановите упражнение") },
            text = {
                Text(
                    "Не пытайтесь «разработать» резкую или нарастающую боль. При опухоли, блокировке, нестабильности, " +
                        "нарастающей ночной боли или слабости обратитесь к врачу.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onSkip(session.id, "Тренировка остановлена из-за боли") }) { Text("Завершить и сохранить") }
            },
            dismissButton = { TextButton(onClick = { showPainStop = false }) { Text("Остаться на паузе") } },
        )
    }
}

@Composable
private fun SetResultDialog(
    target: WorkoutSetTarget,
    elapsedSeconds: Int,
    initialRpe: Int,
    existing: SessionSetLogEntity?,
    onDismiss: () -> Unit,
    onConfirm: (Int?, Double?, Int, Int?, Boolean, String) -> Unit,
) {
    var reps by remember { mutableStateOf((existing?.actualReps ?: target.plannedReps)?.toString().orEmpty()) }
    var load by remember { mutableStateOf(existing?.loadKg?.toString().orEmpty()) }
    var rpe by remember { mutableFloatStateOf((existing?.actualRpe ?: initialRpe).toFloat()) }
    var rir by remember { mutableStateOf(existing?.rir?.toString().orEmpty()) }
    var pain by remember { mutableStateOf(existing?.pain ?: false) }
    var painNote by remember { mutableStateOf(existing?.painNote.orEmpty()) }
    val timedStage = target.workSeconds != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (timedStage) "Результат этапа" else "Результат подхода") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("${target.step.title} · ${formatDuration(elapsedSeconds)}") }
                if (!timedStage) {
                    item {
                        OutlinedTextField(reps, { reps = it.filter(Char::isDigit) }, label = { Text("Повторения") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(load, { load = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } }, label = { Text("Вес, кг (необязательно)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Text("RPE ${if (timedStage) "этапа" else "подхода"}: ${rpe.toInt()}/10")
                    Slider(rpe, { rpe = it }, valueRange = 1f..10f, steps = 8)
                }
                if (!timedStage) {
                    item {
                        OutlinedTextField(rir, { rir = it.filter(Char::isDigit).take(1) }, label = { Text("Повторов в запасе, RIR") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pain, { pain = it })
                        Text("Возникла боль")
                    }
                }
                if (pain) item {
                    OutlinedTextField(painNote, { painNote = it }, label = { Text("Где и при каком движении") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    reps.toIntOrNull().takeUnless { timedStage },
                    load.toDoubleOrNull().takeUnless { timedStage },
                    rpe.toInt(),
                    rir.toIntOrNull()?.coerceIn(0, 5).takeUnless { timedStage },
                    pain,
                    painNote.trim(),
                )
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

private fun blockTypeLabel(type: String): String = when (type) {
    "SUPERSET" -> "суперсет"
    "CIRCUIT" -> "круг"
    "INTERVAL" -> "интервал"
    "AEROBIC" -> "аэробика"
    else -> "обычные подходы"
}

@Composable
private fun ExerciseIllustration(key: String) {
    val resource = illustrationResource(key)
    Card {
        if (resource != null) {
            Image(
                painter = painterResource(resource),
                contentDescription = "Последовательность выполнения упражнения",
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Схема готовится", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@DrawableRes
private fun illustrationResource(key: String): Int? = when (key) {
    "breathing" -> R.drawable.exercise_breathing
    "heel-slide" -> R.drawable.exercise_heel_slide
    "thoracic-mobility", "mobility" -> R.drawable.exercise_thoracic_mobility
    "glute-bridge", "bridge" -> R.drawable.exercise_glute_bridge
    "box-squat" -> R.drawable.exercise_box_squat
    "hip-hinge", "hinge" -> R.drawable.exercise_hip_hinge
    "calf-raise", "calf" -> R.drawable.exercise_calf_raise
    "dead-bug-legs", "core" -> R.drawable.exercise_dead_bug_legs
    "stationary-bike", "bike" -> R.drawable.exercise_stationary_bike
    "walk", "aerobic" -> R.drawable.exercise_walk
    "run-walk" -> R.drawable.exercise_run_walk
    "side-core" -> R.drawable.exercise_side_core
    else -> null
}

@Composable
private fun Instruction(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(text)
    }
}

@Composable
private fun CompletionDialog(
    initialRpe: Int,
    actualDurationSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    var rpe by remember { mutableFloatStateOf(initialRpe.toFloat()) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Итог тренировки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Фактическое время: ${formatDuration(actualDurationSeconds)}")
                Text("Фактический RPE: ${rpe.toInt()}/10")
                Slider(value = rpe, onValueChange = { rpe = it }, valueRange = 1f..10f, steps = 8)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Самочувствие, боль, комментарий") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rpe.toInt(), notes) }) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NotesDialog(title: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Причина") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(notes) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
