package ru.yakovenko.mountainform.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.yakovenko.mountainform.R
import ru.yakovenko.mountainform.data.ExerciseCatalogEntity
import ru.yakovenko.mountainform.data.ExerciseStep
import ru.yakovenko.mountainform.data.ActivityLinkStatus
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.SessionStatus
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.data.SessionStepLogEntity
import ru.yakovenko.mountainform.data.SetTimingStatus
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.ShoulderLoadPhase
import ru.yakovenko.mountainform.data.catalogId
import ru.yakovenko.mountainform.data.imageKey
import ru.yakovenko.mountainform.ui.components.SafetyBanner
import ru.yakovenko.mountainform.ui.formatEpochDay
import ru.yakovenko.mountainform.domain.WorkoutPlanCompiler
import ru.yakovenko.mountainform.domain.WorkoutExecutionState
import ru.yakovenko.mountainform.domain.WorkoutSetTarget
import ru.yakovenko.mountainform.domain.WorkoutTimerMode
import ru.yakovenko.mountainform.domain.ShoulderSafety
import ru.yakovenko.mountainform.domain.durationLooksImplausible
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

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
    shoulderLoadPhase: String = ShoulderLoadPhase.RESTRICTED,
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
    onRestoreSkipped: (String) -> Unit = {},
    importedActivities: List<ImportedActivityEntity> = emptyList(),
    onReplaceSessionActivities: (String, List<String>) -> Unit = { _, _ -> },
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
    val restoredAtEpochMillis = remember(session.id) { System.currentTimeMillis() }
    val staleExecutionAtOpen = remember(session.id) {
        initialExecutionState?.hasStaleRunningTimer(restoredAtEpochMillis) == true
    }
    var executionState by remember(session.id) {
        mutableStateOf(
            initialExecutionState?.restoreForForeground(restoredAtEpochMillis)?.copy(targetIndex = restoredTarget)
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
    var showRestoreSkipped by remember { mutableStateOf(false) }
    var showSetResult by remember { mutableStateOf(false) }
    var editResultOnly by remember { mutableStateOf(false) }
    var showResetTimerConfirm by remember { mutableStateOf(false) }
    var showPlanDetails by remember(session.id) { mutableStateOf(false) }
    var showSafetyDetails by remember(session.id) { mutableStateOf(false) }
    var showStaleTimerWarning by remember(session.id) { mutableStateOf(staleExecutionAtOpen) }
    var showGarminPicker by remember(session.id) { mutableStateOf(false) }
    var garminSelection by remember(session.id) { mutableStateOf(emptySet<String>()) }
    var showExerciseMenu by remember(session.id) { mutableStateOf(false) }
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
    val allTargetsCompleted = targets.isNotEmpty() && completedCount == targets.size
    val emptyPlan = targets.isEmpty()
    val shoulderPlanConflict = shoulderRestrictionActive && steps.any { ShoulderSafety.conflicts(it, shoulderLoadPhase) }
    val trainingBlocked = loadBlocked || emptyPlan || shoulderPlanConflict
    val currentLog = target?.let { current ->
        sessionSetLogs.firstOrNull {
            it.stepId == current.step.id && it.roundIndex == current.roundIndex && it.setIndex == current.setIndex
        }
    }
    var showTechnique by remember(session.id, targetIndex) { mutableStateOf(false) }
    val linkedActivities = importedActivities.filter { it.linkedSessionId == session.id }
    val garminCandidates = remember(importedActivities, session.id, session.plannedEpochDay) {
        sessionGarminCandidates(session, importedActivities)
    }

    fun persistExecution(next: WorkoutExecutionState) {
        executionState = next
        clockEpochMillis = System.currentTimeMillis()
        onExecutionStateChanged(next)
    }

    LaunchedEffect(session.id) {
        if (initialExecutionState != null && executionState != initialExecutionState) {
            onExecutionStateChanged(executionState)
        }
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

    fun startOrResumeWorkout() {
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
    }

    fun toggleWorkoutPause() {
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
    }

    fun toggleSetTimer() {
        val currentTarget = target ?: return
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
                    workoutTickStartedAtEpochMillis = if (snapshot.workoutStarted) snapshot.workoutTickStartedAtEpochMillis else now,
                    timerMode = WorkoutTimerMode.SET,
                    timerTickStartedAtEpochMillis = now,
                    setElapsedSeconds = if (restartTimedSet) 0 else snapshot.setElapsedSeconds,
                    workRemainingSeconds = if (restartTimedSet) currentTarget.workSeconds else snapshot.workRemainingSeconds,
                )
            },
        )
    }

    fun advanceOrRequestSkip() {
        when {
            currentLog == null -> showSkipStage = true
            targetIndex < targets.lastIndex -> selectTarget(targetIndex + 1)
            else -> showCompletion = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session.id, executionState) {
        val observer = LifecycleEventObserver { _, event ->
            val now = System.currentTimeMillis()
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (executionState.workoutStarted && !executionState.paused) {
                        persistExecution(
                            executionState.snapshotAt(now).copy(workoutTickStartedAtEpochMillis = null),
                        )
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (
                        executionState.workoutStarted &&
                        !executionState.paused &&
                        executionState.workoutTickStartedAtEpochMillis == null
                    ) {
                        persistExecution(executionState.copy(workoutTickStartedAtEpochMillis = now))
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        session.id,
        executionState.workoutStarted,
        executionState.paused,
        executionState.timerMode,
        executionState.timerTickStartedAtEpochMillis,
    ) {
        while (session.status == SessionStatus.PLANNED && executionState.workoutStarted && !executionState.paused) {
            val now = System.currentTimeMillis()
            if (executionState.hasStaleRunningTimer(now)) {
                persistExecution(
                    executionState.snapshotAt(now).copy(
                        paused = true,
                        workoutTickStartedAtEpochMillis = null,
                        timerMode = WorkoutTimerMode.NONE,
                        timerTickStartedAtEpochMillis = null,
                    ),
                )
                showStaleTimerWarning = true
                break
            }
            clockEpochMillis = now
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
    LaunchedEffect(trainingBlocked, executionState.workoutStarted, executionState.paused) {
        if (trainingBlocked && executionState.workoutStarted && !executionState.paused) {
            pauseWorkoutForSafety()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (session.status == SessionStatus.PLANNED && executionState.workoutStarted) {
                        IconButton(onClick = { showOverview = !showOverview }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = if (showOverview) "К упражнению" else "Весь план")
                        }
                        IconButton(onClick = ::toggleWorkoutPause, enabled = !(paused && trainingBlocked)) {
                            Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "Продолжить" else "Пауза")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (session.status == SessionStatus.PLANNED && showOverview) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            if (allTargetsCompleted) showCompletion = true else startOrResumeWorkout()
                        },
                        enabled = allTargetsCompleted || !trainingBlocked,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("start_workout_button"),
                    ) {
                        Icon(if (allTargetsCompleted) Icons.Default.Check else Icons.Default.PlayArrow, contentDescription = null)
                        Text(
                            when {
                                allTargetsCompleted -> "  Перейти к итогу"
                                executionState.workoutStarted -> "  Продолжить"
                                else -> "  Начать тренировку"
                            },
                        )
                    }
                }
            } else if (session.status == SessionStatus.PLANNED && target != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                pauseWorkoutForSafety()
                                showPainStop = true
                            },
                            modifier = Modifier.testTag("pain_stop_button"),
                        ) { Text("Боль") }
                        when {
                            paused -> Button(
                                onClick = ::toggleWorkoutPause,
                                enabled = !trainingBlocked,
                                modifier = Modifier.weight(1f).testTag("resume_workout_button"),
                            ) { Text("Продолжить тренировку") }
                            executionState.timerMode == WorkoutTimerMode.REST -> Button(
                                onClick = { finishRest(skipped = restSeconds > 0) },
                                modifier = Modifier.weight(1f).testTag("finish_rest_button"),
                            ) { Text(if (restSeconds > 0) "Начать раньше" else "К следующему") }
                            currentLog != null -> Button(
                                onClick = ::advanceOrRequestSkip,
                                modifier = Modifier.weight(1f).testTag("next_stage_button"),
                            ) { Text(if (targetIndex == targets.lastIndex) "Перейти к итогу" else "Следующий этап") }
                            target.workSeconds != null && setRunning -> Button(
                                onClick = ::quickCompleteCurrentTarget,
                                modifier = Modifier.weight(1f).testTag("complete_set_button"),
                            ) { Text("Завершить этап") }
                            target.workSeconds != null -> Button(
                                onClick = ::toggleSetTimer,
                                enabled = !trainingBlocked,
                                modifier = Modifier.weight(1f).testTag("set_timer_button"),
                            ) { Text(if (setSeconds > 0) "Продолжить этап" else "Начать этап") }
                            else -> Button(
                                onClick = ::quickCompleteCurrentTarget,
                                enabled = !trainingBlocked,
                                modifier = Modifier.weight(1f).testTag("complete_set_button"),
                            ) { Text("Подход выполнен") }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("session_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatEpochDay(session.plannedEpochDay), color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (executionState.workoutStarted) {
                                "${formatDuration(workoutSeconds)} · RPE ${session.targetRpe}"
                            } else {
                                "${session.durationMinutes} мин · RPE ${session.targetRpe}"
                            },
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (targets.isEmpty()) 0f else completedCount.toFloat() / targets.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (executionState.workoutStarted) "$completedCount из ${targets.size} этапов" else "${steps.size} упражнений · ${targets.size} этапов",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (emptyPlan) {
                item {
                    SafetyBanner(
                        "В плане тренировки нет этапов. Начать её нельзя: сначала обновите план.",
                    )
                }
            } else if (shoulderPlanConflict) {
                item {
                    SafetyBanner(
                        "План содержит упражнение, несовместимое с активным ограничением плеча. " +
                            "Приложение не заменяет упражнение автоматически — обновите план.",
                    )
                }
            } else if (loadBlocked) {
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
                            Text("Изменить состояние")
                        }
                    }
                }
            } else if (shoulderRestrictionActive || adaptationRequired) {
                item {
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                when {
                                    adaptationRequired -> "Сегодня снизить нагрузку"
                                    else -> "Плечо: ограничение активно"
                                },
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { showSafetyDetails = true }) { Text("Подробнее") }
                        }
                    }
                }
            }

            if (showOverview && session.status == SessionStatus.PLANNED) {
                item {
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text("План тренировки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(session.objective, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            targets.groupBy { it.blockId }.values.forEach { blockTargets ->
                                val first = blockTargets.first()
                                val uniqueExercises = blockTargets.distinctBy { it.step.id }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        first.blockTitle,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text("${uniqueExercises.size} упр. · ${blockTargets.size} этапов", style = MaterialTheme.typography.bodySmall)
                                }
                                if (showPlanDetails) {
                                    uniqueExercises.forEach { blockTarget ->
                                        val count = blockTargets.count { it.step.id == blockTarget.step.id }
                                        Text(
                                            "• ${blockTarget.step.title} — ${blockTarget.step.prescription}" +
                                                if (count > 1) " · $count этапа" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { showPlanDetails = !showPlanDetails }) {
                                Text(if (showPlanDetails) "Скрыть подробности" else "Полный план")
                            }
                        }
                    }
                }
            }

            if (showOverview || session.status != SessionStatus.PLANNED) {
                item {
                    SessionGarminCard(
                        linkedActivities = linkedActivities,
                        onManage = {
                            garminSelection = linkedActivities.mapTo(mutableSetOf()) { it.id }
                            showGarminPicker = true
                        },
                    )
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
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showTechnique = !showTechnique }) {
                            Text(if (showTechnique) "Скрыть" else "Техника")
                        }
                    }
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
                                Text("План: ${formatDuration(executionState.restPlannedSeconds)} · прошло: ${formatDuration(restElapsedSeconds)}")
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
                                if (currentTarget.workSeconds != null) {
                                    Text(if (setRunning) "Осталось" else "Таймер этапа", fontWeight = FontWeight.Bold)
                                    Text(formatDuration(workRemaining), style = MaterialTheme.typography.headlineMedium)
                                    if (!setRunning && currentLog == null) {
                                        Text("Запустится по кнопке ниже", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (setSeconds > 0) {
                                    Text("Время подхода", fontWeight = FontWeight.Bold)
                                    Text(formatDuration(setSeconds), style = MaterialTheme.typography.headlineMedium)
                                } else {
                                    Text("Отметьте подход одним нажатием", fontWeight = FontWeight.SemiBold)
                                }
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
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = {
                                            editResultOnly = currentLog != null
                                            if (currentLog == null) {
                                                val now = System.currentTimeMillis()
                                                persistExecution(
                                                    executionState.snapshotAt(now).copy(
                                                        timerMode = WorkoutTimerMode.NONE,
                                                        timerTickStartedAtEpochMillis = null,
                                                    ),
                                                )
                                            }
                                            showSetResult = true
                                        },
                                        enabled = !trainingBlocked && !paused,
                                        modifier = Modifier.testTag("detailed_set_result_button"),
                                    ) {
                                        Text(if (currentLog == null) "Записать подробнее" else "Изменить запись")
                                    }
                                    val timerHasProgress = currentTarget.workSeconds?.let { workRemaining < it } ?: (setSeconds > 0)
                                    Box {
                                        IconButton(onClick = { showExerciseMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Ещё действия")
                                        }
                                        DropdownMenu(expanded = showExerciseMenu, onDismissRequest = { showExerciseMenu = false }) {
                                            if (currentTarget.workSeconds == null && currentLog == null) {
                                                DropdownMenuItem(
                                                    text = { Text(if (setRunning) "Остановить секундомер" else "Запустить секундомер") },
                                                    onClick = { showExerciseMenu = false; toggleSetTimer() },
                                                )
                                            }
                                            if (timerHasProgress) {
                                                DropdownMenuItem(
                                                    text = { Text("Сбросить таймер") },
                                                    onClick = { showExerciseMenu = false; showResetTimerConfirm = true },
                                                    modifier = Modifier.testTag("reset_set_timer_button"),
                                                )
                                            }
                                            if (targetIndex > 0) {
                                                DropdownMenuItem(
                                                    text = { Text("Предыдущий этап") },
                                                    onClick = { showExerciseMenu = false; selectTarget(targetIndex - 1) },
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text(if (currentLog == null) "Пропустить этап" else "Следующий этап") },
                                                onClick = { showExerciseMenu = false; advanceOrRequestSkip() },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Пропустить всю тренировку") },
                                                onClick = { showExerciseMenu = false; showSkip = true },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showTechnique) {
                    item { ExerciseIllustration(current.imageKey()) }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            if (session.actualDurationSeconds > 0) {
                                Text("Фактическое время: ${formatDuration(session.actualDurationSeconds)}")
                            }
                            if (session.completionNotes.isNotBlank()) Text(session.completionNotes)
                            if (session.status == SessionStatus.SKIPPED) {
                                OutlinedButton(
                                    onClick = { showRestoreSkipped = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Вернуть тренировку в план") }
                            }
                        }
                    }
                }
            } else if (showOverview) {
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
            plannedDurationMinutes = session.durationMinutes,
            onDismiss = { showCompletion = false },
            onConfirm = { rpe, notes, durationSeconds -> onComplete(session.id, rpe, notes, durationSeconds) },
        )
    }
    if (showStaleTimerWarning) {
        AlertDialog(
            onDismissRequest = { showStaleTimerWarning = false },
            title = { Text("Таймер поставлен на паузу") },
            text = {
                Text(
                    "Приложение нашло долго незавершённый таймер и не добавило прошедшие часы к тренировке. Продолжите сеанс или укажите фактическое время в итоге.",
                )
            },
            confirmButton = { TextButton(onClick = { showStaleTimerWarning = false }) { Text("Понятно") } },
        )
    }
    if (showGarminPicker) {
        GarminSessionPickerDialog(
            session = session,
            candidates = garminCandidates,
            selectedIds = garminSelection,
            onSelectionChange = { garminSelection = it },
            onDismiss = { showGarminPicker = false },
            onConfirm = {
                onReplaceSessionActivities(session.id, garminSelection.toList())
                showGarminPicker = false
            },
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
    if (showRestoreSkipped) {
        AlertDialog(
            onDismissRequest = { showRestoreSkipped = false },
            title = { Text("Вернуть тренировку в план?") },
            text = {
                Text(
                    "Она снова станет запланированной. Уже записанные подходы и отметки сохранятся.",
                )
            },
            confirmButton = {
                Button(onClick = { showRestoreSkipped = false; onRestoreSkipped(session.id) }) {
                    Text("Вернуть")
                }
            },
            dismissButton = { TextButton(onClick = { showRestoreSkipped = false }) { Text("Отмена") } },
        )
    }
    if (showSafetyDetails) {
        AlertDialog(
            onDismissRequest = { showSafetyDetails = false },
            title = { Text("Ограничения на сегодня") },
            text = {
                Text(
                    buildString {
                        if (adaptationRequired) {
                            append(readinessRecommendation)
                            append(" Приложение не заменяет упражнения автоматически. ")
                        }
                        if (shoulderRestrictionActive) {
                            append("Для плеча используйте только безболезненный диапазон. Не добавляйте подтягивания, брусья и движения над головой.")
                        }
                    }.trim(),
                )
            },
            confirmButton = { TextButton(onClick = { showSafetyDetails = false }) { Text("Понятно") } },
            dismissButton = {
                if (adaptationRequired) {
                    TextButton(onClick = { showSafetyDetails = false; onEditReadiness() }) { Text("Изменить состояние") }
                }
            },
        )
    }
}

@Composable
private fun SessionGarminCard(
    linkedActivities: List<ImportedActivityEntity>,
    onManage: () -> Unit,
) {
    val groups = remember(linkedActivities) { linkedGarminGroups(linkedActivities) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (linkedActivities.isEmpty()) "Garmin-факт" else "Garmin-факт · ${linkedActivities.size}",
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onManage) { Text(if (linkedActivities.isEmpty()) "Добавить" else "Изменить") }
            }
            if (groups.isEmpty()) {
                Text(
                    "Можно связать одну или несколько записей Garmin с этой тренировкой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                groups.forEach { group ->
                    Text(
                        buildString {
                            append(group.typeLabel)
                            if (group.count > 1) append(" · ${group.count} записи")
                            append(" · ${formatGarminDuration(group.durationSeconds)}")
                            group.distanceMeters?.let { append(" · ${formatGarminDistance(it)} км") }
                            group.elevationMeters?.let { append(" · +${it.toInt()} м") }
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (group.averageHeartRate != null || group.maxHeartRate != null) {
                        Text(
                            buildString {
                                group.averageHeartRate?.let { append("средний пульс ${it.toInt()}") }
                                group.maxHeartRate?.let { if (isNotEmpty()) append(" · "); append("макс. ${it.toInt()}") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GarminSessionPickerDialog(
    session: TrainingSessionEntity,
    candidates: List<ImportedActivityEntity>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Garmin для тренировки") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(session.title, fontWeight = FontWeight.Bold)
                    Text(
                        "Записи за 7 дней до и после. Можно выбрать несколько.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (candidates.isEmpty()) {
                    item { Text("Доступных Garmin-активностей в этом диапазоне нет.") }
                } else {
                    items(candidates.size, key = { candidates[it].id }) { index ->
                        val activity = candidates[index]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = activity.id in selectedIds,
                                onCheckedChange = { checked ->
                                    onSelectionChange(
                                        if (checked) selectedIds + activity.id else selectedIds - activity.id,
                                    )
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(friendlyActivityType(activity.activityType), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatGarminTime(activity.startAtEpochMillis)} · ${formatGarminDuration(activity.durationSeconds)}" +
                                        activity.distanceMeters?.let { " · ${formatGarminDistance(it)} км" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

internal fun sessionGarminCandidates(
    session: TrainingSessionEntity,
    activities: List<ImportedActivityEntity>,
): List<ImportedActivityEntity> = activities
    .asSequence()
    .filter { it.status != ActivityLinkStatus.IGNORED }
    .filter { it.linkedSessionId == null || it.linkedSessionId == session.id }
    .filter {
        val day = Instant.ofEpochMilli(it.startAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        abs(day - session.plannedEpochDay) <= 7L
    }
    .sortedByDescending { it.startAtEpochMillis }
    .toList()

private fun formatGarminDuration(seconds: Long): String = when {
    seconds >= 3_600 -> "%d:%02d:%02d".format(seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatGarminDistance(meters: Double): String =
    String.format(java.util.Locale.US, if (meters < 10_000) "%.2f" else "%.1f", meters / 1_000.0)

private fun formatGarminTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))

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
    val parsedReps = reps.toIntOrNull()
    val parsedLoad = load.toDoubleOrNull()
    val parsedRir = rir.toIntOrNull()
    val repsInvalid = !timedStage && reps.isNotBlank() && (parsedReps == null || parsedReps < 0 || parsedReps > 999)
    val loadInvalid = !timedStage && load.isNotBlank() && (parsedLoad == null || parsedLoad < 0.0 || parsedLoad > 500.0)
    val rirInvalid = !timedStage && rir.isNotBlank() && (parsedRir == null || parsedRir < 0 || parsedRir > 5)
    val painNoteInvalid = pain && painNote.isBlank()
    val resultValid = !repsInvalid && !loadInvalid && !rirInvalid && !painNoteInvalid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (timedStage) "Результат этапа" else "Результат подхода") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("${target.step.title} · ${formatDuration(elapsedSeconds)}") }
                if (!timedStage) {
                    item {
                        OutlinedTextField(
                            reps,
                            { reps = it.filter(Char::isDigit).take(3) },
                            label = { Text("Повторения") },
                            isError = repsInvalid,
                            supportingText = { if (repsInvalid) Text("Допустимо 0–999") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            load,
                            { load = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Вес, кг (необязательно)") },
                            isError = loadInvalid,
                            supportingText = { if (loadInvalid) Text("Допустимо 0–500 кг") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Text("RPE ${if (timedStage) "этапа" else "подхода"}: ${rpe.toInt()}/10")
                    Slider(rpe, { rpe = it }, valueRange = 1f..10f, steps = 8)
                }
                if (!timedStage) {
                    item {
                        OutlinedTextField(
                            rir,
                            { rir = it.filter(Char::isDigit).take(1) },
                            label = { Text("Повторов в запасе, RIR") },
                            isError = rirInvalid,
                            supportingText = { if (rirInvalid) Text("Допустимо 0–5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pain, { pain = it })
                        Text("Возникла боль")
                    }
                }
                if (pain) item {
                    OutlinedTextField(
                        painNote,
                        { painNote = it },
                        label = { Text("Где и при каком движении") },
                        isError = painNoteInvalid,
                        supportingText = { if (painNoteInvalid) Text("Опишите боль, чтобы не потерять важный сигнал") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = resultValid,
                onClick = {
                    onConfirm(
                        parsedReps.takeUnless { timedStage },
                        parsedLoad.takeUnless { timedStage },
                        rpe.toInt(),
                        parsedRir.takeUnless { timedStage },
                        pain,
                        painNote.trim(),
                    )
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3_600) {
        "%d:%02d:%02d".format(safe / 3_600, (safe % 3_600) / 60, safe % 60)
    } else {
        "%d:%02d".format(safe / 60, safe % 60)
    }
}

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
    "leg-press" -> R.drawable.exercise_leg_press
    "leg-curl" -> R.drawable.exercise_leg_curl
    "step-down" -> R.drawable.exercise_step_down
    "hip-thrust-machine" -> R.drawable.exercise_hip_thrust_machine
    "chest-supported-row" -> R.drawable.exercise_chest_supported_row
    "biceps-cable" -> R.drawable.exercise_biceps_cable
    "triceps-cable" -> R.drawable.exercise_triceps_cable
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
    plannedDurationMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Int) -> Unit,
) {
    var rpe by remember { mutableFloatStateOf(initialRpe.toFloat()) }
    var notes by remember { mutableStateOf("") }
    val suspiciousInitialDuration = durationLooksImplausible(actualDurationSeconds, plannedDurationMinutes)
    val initialDurationMinutes = if (suspiciousInitialDuration) {
        plannedDurationMinutes
    } else {
        ((actualDurationSeconds + 30) / 60).coerceAtLeast(1)
    }
    var durationMinutes by remember { mutableStateOf(initialDurationMinutes.toString()) }
    val parsedDurationMinutes = durationMinutes.toIntOrNull()
    val durationInvalid = parsedDurationMinutes == null || parsedDurationMinutes !in 1..720
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Итог тренировки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (suspiciousInitialDuration) {
                    SafetyBanner(
                        "Таймер показал ${formatDuration(actualDurationSeconds)}, что не похоже на фактическую длительность. Проверьте время перед сохранением.",
                    )
                }
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter(Char::isDigit).take(3) },
                    label = { Text("Фактическое время, мин") },
                    isError = durationInvalid,
                    supportingText = { if (durationInvalid) Text("Укажите 1–720 минут") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
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
        confirmButton = {
            Button(
                enabled = !durationInvalid,
                onClick = { onConfirm(rpe.toInt(), notes, requireNotNull(parsedDurationMinutes) * 60) },
            ) { Text("Завершить") }
        },
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
