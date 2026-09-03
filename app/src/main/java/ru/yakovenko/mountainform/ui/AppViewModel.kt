package ru.yakovenko.mountainform.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yakovenko.mountainform.data.BodyMetricEntity
import ru.yakovenko.mountainform.data.BackupPreview
import ru.yakovenko.mountainform.data.AppSettingsEntity
import ru.yakovenko.mountainform.data.ExerciseCatalogEntity
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.ImportPreview
import ru.yakovenko.mountainform.data.MountainFormRepository
import ru.yakovenko.mountainform.data.PracticeLogEntity
import ru.yakovenko.mountainform.data.PostureAssessmentEntity
import ru.yakovenko.mountainform.data.ReadinessCheckEntity
import ru.yakovenko.mountainform.data.ReopenCompletedMode
import ru.yakovenko.mountainform.data.RescheduleEventEntity
import ru.yakovenko.mountainform.data.SessionStepLogEntity
import ru.yakovenko.mountainform.data.SessionSetLogEntity
import ru.yakovenko.mountainform.data.ReviewCheckpointEntity
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.UserProfileEntity
import ru.yakovenko.mountainform.data.WorkoutExecutionStore
import ru.yakovenko.mountainform.domain.ReadinessDecision
import ru.yakovenko.mountainform.domain.ReadinessLevel
import ru.yakovenko.mountainform.domain.TrainingSafety
import ru.yakovenko.mountainform.domain.WorkoutExecutionState
import ru.yakovenko.mountainform.health.HealthConnectManager
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.health.FitActivityImporter
import ru.yakovenko.mountainform.reminders.ReminderScheduler
import ru.yakovenko.mountainform.sync.SharedFolderSyncManager
import ru.yakovenko.mountainform.sync.SecureTokenStore
import ru.yakovenko.mountainform.sync.YandexDiskSyncManager
import ru.yakovenko.mountainform.sync.YandexSyncProgress
import ru.yakovenko.mountainform.sync.YandexSyncStage
import ru.yakovenko.mountainform.sync.YandexSyncWorker
import ru.yakovenko.mountainform.update.AppUpdateManager
import ru.yakovenko.mountainform.update.UpdateOperation
import ru.yakovenko.mountainform.update.UpdateState
import java.time.LocalDate

data class AppUiState(
    val profile: UserProfileEntity? = null,
    val goals: List<GoalEventEntity> = emptyList(),
    val sessions: List<TrainingSessionEntity> = emptyList(),
    val readiness: List<ReadinessCheckEntity> = emptyList(),
    val bodyMetrics: List<BodyMetricEntity> = emptyList(),
    val practices: List<PracticeLogEntity> = emptyList(),
    val exerciseCatalog: List<ExerciseCatalogEntity> = emptyList(),
    val settings: AppSettingsEntity? = null,
    val rescheduleEvents: List<RescheduleEventEntity> = emptyList(),
    val postureAssessments: List<PostureAssessmentEntity> = emptyList(),
    val stepLogs: List<SessionStepLogEntity> = emptyList(),
    val setLogs: List<SessionSetLogEntity> = emptyList(),
    val reviewCheckpoints: List<ReviewCheckpointEntity> = emptyList(),
    val importedActivities: List<ImportedActivityEntity> = emptyList(),
) {
    val todayCheck: ReadinessCheckEntity?
        get() = readiness.firstOrNull { it.epochDay == LocalDate.now().toEpochDay() }

    val readinessDecision: ReadinessDecision
        get() = TrainingSafety.evaluate(todayCheck)

    val nextSession: TrainingSessionEntity?
        get() = sessions.firstOrNull {
            it.status == "PLANNED" && it.plannedEpochDay >= LocalDate.now().toEpochDay()
        } ?: sessions.firstOrNull { it.status == "PLANNED" }
}

data class DataSyncState(
    val running: Boolean = false,
    val stage: String = "",
    val transferredBytes: Long = 0,
    val totalBytes: Long? = null,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let {
            (transferredBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

class AppViewModel(
    private val repository: MountainFormRepository,
    private val healthConnectManager: HealthConnectManager,
    private val appUpdateManager: AppUpdateManager,
    private val reminderScheduler: ReminderScheduler,
    private val sharedFolderSyncManager: SharedFolderSyncManager,
    private val yandexDiskSyncManager: YandexDiskSyncManager,
    private val secureTokenStore: SecureTokenStore,
    private val fitActivityImporter: FitActivityImporter,
    private val workoutExecutionStore: WorkoutExecutionStore,
) : ViewModel() {
    private val trainingState = combine(
        repository.profile,
        repository.goals,
        repository.sessions,
    ) { profile, goals, sessions -> Triple(profile, goals, sessions) }

    private val wellnessState = combine(
        repository.readiness,
        repository.bodyMetrics,
        repository.practices,
    ) { readiness, bodyMetrics, practices -> Triple(readiness, bodyMetrics, practices) }

    private data class ExtendedState(
        val exerciseCatalog: List<ExerciseCatalogEntity>,
        val settings: AppSettingsEntity?,
        val rescheduleEvents: List<RescheduleEventEntity>,
        val postureAssessments: List<PostureAssessmentEntity>,
        val stepLogs: List<SessionStepLogEntity>,
    )

    private val extendedState = combine(
        repository.exerciseCatalog,
        repository.settings,
        repository.rescheduleEvents,
        repository.postureAssessments,
        repository.stepLogs,
    ) { exerciseCatalog, settings, rescheduleEvents, postureAssessments, stepLogs ->
        ExtendedState(exerciseCatalog, settings, rescheduleEvents, postureAssessments, stepLogs)
    }

    private data class ActivityState(
        val setLogs: List<SessionSetLogEntity>,
        val reviewCheckpoints: List<ReviewCheckpointEntity>,
        val importedActivities: List<ImportedActivityEntity>,
    )

    private val activityState = combine(
        repository.setLogs,
        repository.reviewCheckpoints,
        repository.importedActivities,
    ) { setLogs, checkpoints, activities -> ActivityState(setLogs, checkpoints, activities) }

    val uiState: StateFlow<AppUiState> = combine(trainingState, wellnessState, extendedState, activityState) { training, wellness, extended, activity ->
        AppUiState(
            profile = training.first,
            goals = training.second,
            sessions = training.third,
            readiness = wellness.first,
            bodyMetrics = wellness.second,
            practices = wellness.third,
            exerciseCatalog = extended.exerciseCatalog,
            settings = extended.settings,
            rescheduleEvents = extended.rescheduleEvents,
            postureAssessments = extended.postureAssessments,
            stepLogs = extended.stepLogs,
            setLogs = activity.setLogs,
            reviewCheckpoints = activity.reviewCheckpoints,
            importedActivities = activity.importedActivities,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    val healthSummary = MutableStateFlow(HealthSummary())
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val backupPreview = MutableStateFlow<BackupPreview?>(null)
    val message = MutableStateFlow<String?>(null)
    val updateState = MutableStateFlow(UpdateState())
    val syncState = MutableStateFlow(DataSyncState())
    val yandexConnected = MutableStateFlow(secureTokenStore.hasToken())
    private var pendingSharedPlanName: String? = null
    private var pendingSharedPlanJson: String? = null
    private var pendingPlanFromYandex: Boolean = false

    init {
        viewModelScope.launch {
            repository.initialize()
            refreshHealth()
            refreshUpdateState(showErrors = false)
        }
    }

    fun saveReadiness(
        sleep: Int,
        energy: Int,
        fatigue: Int,
        soreness: Int,
        shoulderPain: Int,
        kneePain: Int,
        illness: Boolean,
        notes: String,
    ) {
        viewModelScope.launch {
            repository.saveReadiness(
                ReadinessCheckEntity(
                    epochDay = LocalDate.now().toEpochDay(),
                    sleep = sleep,
                    energy = energy,
                    fatigue = fatigue,
                    soreness = soreness,
                    shoulderPain = shoulderPain,
                    kneePain = kneePain,
                    illness = illness,
                    notes = notes.trim(),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            message.value = "Состояние сохранено"
            automaticSyncIfEnabled()
        }
    }

    fun saveBodyMetric(
        weightKg: Double?,
        waistCm: Double?,
        proteinGoalMet: Boolean,
        produceGoalMet: Boolean,
        hydrationGoalMet: Boolean,
        alcoholFree: Boolean,
        notes: String,
    ) {
        viewModelScope.launch {
            repository.saveBodyMetric(
                BodyMetricEntity(
                    epochDay = LocalDate.now().toEpochDay(),
                    weightKg = weightKg,
                    waistCm = waistCm,
                    proteinGoalMet = proteinGoalMet,
                    produceGoalMet = produceGoalMet,
                    hydrationGoalMet = hydrationGoalMet,
                    alcoholFree = alcoholFree,
                    notes = notes.trim(),
                ),
            )
            message.value = "Метрики сохранены"
            automaticSyncIfEnabled()
        }
    }

    fun updateProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            message.value = "Профиль обновлён"
        }
    }

    fun updateGoal(goal: GoalEventEntity) {
        viewModelScope.launch {
            repository.updateGoal(goal)
            message.value = "Цель обновлена"
        }
    }

    fun rescheduleSession(id: String, newEpochDay: Long, reason: String) {
        viewModelScope.launch {
            runCatching { repository.rescheduleSession(id, newEpochDay, reason) }
                .onSuccess { message.value = "Тренировка перенесена" }
                .onFailure { message.value = it.message ?: "Не удалось перенести тренировку" }
        }
    }

    fun setStepCompleted(sessionId: String, stepId: String, completed: Boolean) {
        viewModelScope.launch { repository.setStepCompleted(sessionId, stepId, completed) }
    }

    fun saveSetLog(log: SessionSetLogEntity) {
        viewModelScope.launch { repository.saveSetLog(log) }
    }

    fun importFit(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { fitActivityImporter.import(uri) } }
                .onSuccess {
                    repository.upsertImportedActivities(it)
                    message.value = "FIT импортирован: ${it.size} активност${if (it.size == 1) "ь" else "и"}"
                }
                .onFailure { message.value = it.message ?: "Не удалось импортировать FIT" }
        }
    }

    fun linkActivity(activityId: String, sessionId: String?) {
        viewModelScope.launch {
            runCatching { repository.linkActivity(activityId, sessionId) }
                .onSuccess {
                    message.value = if (sessionId == null) "Связь удалена" else "Активность связана с планом"
                }
                .onFailure { message.value = it.message ?: "Не удалось связать активность" }
        }
    }

    fun replaceSessionActivities(sessionId: String, selectedActivityIds: List<String>) {
        viewModelScope.launch {
            runCatching { repository.replaceSessionActivities(sessionId, selectedActivityIds) }
                .onSuccess {
                    message.value = when (selectedActivityIds.size) {
                        0 -> "Связи Garmin удалены"
                        1 -> "Garmin-активность связана с тренировкой"
                        else -> "С тренировкой связано ${selectedActivityIds.size} Garmin-активности"
                    }
                }
                .onFailure { message.value = it.message ?: "Не удалось обновить связи Garmin" }
        }
    }

    fun ignoreActivity(activityId: String) {
        viewModelScope.launch {
            repository.ignoreActivity(activityId)
            message.value = "Активность скрыта; её можно вернуть"
        }
    }

    fun restoreActivity(activityId: String) {
        viewModelScope.launch {
            runCatching { repository.linkActivity(activityId, null) }
                .onSuccess { message.value = "Активность вернута к привязке" }
                .onFailure { message.value = it.message ?: "Не удалось вернуть активность" }
        }
    }

    fun savePostureAssessment(selfRating: Int, notes: String, photoUris: List<String?>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.savePostureAssessment(
                PostureAssessmentEntity(
                    id = now.toString(),
                    epochDay = LocalDate.now().toEpochDay(),
                    frontPhotoUri = photoUris.getOrNull(0),
                    sidePhotoUri = photoUris.getOrNull(1),
                    backPhotoUri = photoUris.getOrNull(2),
                    selfRating = selfRating,
                    notes = notes.trim(),
                    createdAtEpochMillis = now,
                ),
            )
            message.value = "Оценка осанки сохранена локально"
            automaticSyncIfEnabled()
        }
    }

    fun updateSettings(settings: AppSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(settings)
            reminderScheduler.update(settings)
            if (settings.automaticSync && settings.yandexSyncEnabled) {
                YandexSyncWorker.enqueue(reminderScheduler.context)
            }
            message.value = "Настройки сохранены"
        }
    }

    fun connectYandex(token: String, rootPath: String) {
        viewModelScope.launch {
            runCatching { yandexDiskSyncManager.connect(token, rootPath) }
                .onSuccess { accountLabel ->
                    val current = uiState.value.settings ?: AppSettingsEntity()
                    repository.updateSettings(
                        current.copy(
                            yandexSyncEnabled = true,
                            yandexRootPath = rootPath,
                            yandexAccountLabel = accountLabel,
                            lastSyncMessage = "Яндекс Диск подключён",
                        ),
                    )
                    yandexConnected.value = true
                    message.value = "Яндекс Диск подключён"
                }
                .onFailure { message.value = it.message ?: "Не удалось подключить Яндекс Диск" }
        }
    }

    fun disconnectYandex() {
        viewModelScope.launch {
            yandexDiskSyncManager.disconnect()
            val current = uiState.value.settings ?: AppSettingsEntity()
            repository.updateSettings(current.copy(yandexSyncEnabled = false, yandexAccountLabel = ""))
            yandexConnected.value = false
            message.value = "Яндекс Диск отключён; локальные данные сохранены"
        }
    }

    fun reportYandexLoginMessage(value: String) {
        message.value = value
    }

    fun loadWorkoutExecution(sessionId: String): WorkoutExecutionState? = workoutExecutionStore.load(sessionId)

    fun saveWorkoutExecution(state: WorkoutExecutionState) = workoutExecutionStore.save(state)

    fun clearWorkoutExecution(sessionId: String) = workoutExecutionStore.clear(sessionId)

    fun syncYandex() {
        if (syncState.value.running) return
        val current = uiState.value.settings ?: AppSettingsEntity()
        syncState.value = DataSyncState(running = true, stage = "Подготавливаем обмен…")
        viewModelScope.launch {
            runCatching { yandexDiskSyncManager.sync(current.yandexRootPath, ::updateYandexSyncProgress) }
                .onSuccess { result ->
                    pendingSharedPlanName = result.pendingPlanName
                    pendingSharedPlanJson = result.pendingPlanJson
                    pendingPlanFromYandex = result.pendingPlanJson != null
                    repository.updateSettings(
                        current.copy(lastSyncAtEpochMillis = System.currentTimeMillis(), lastSyncMessage = result.message),
                    )
                    syncState.value = DataSyncState(stage = result.message, transferredBytes = 1, totalBytes = 1)
                    result.pendingPlanJson?.let(::previewImport)
                    message.value = result.message
                }
                .onFailure {
                    val errorMessage = "Ошибка синхронизации: ${it.message ?: "Яндекс Диск недоступен"}"
                    repository.updateSettings(current.copy(lastSyncMessage = errorMessage))
                    syncState.value = DataSyncState(stage = errorMessage)
                    message.value = errorMessage
                }
        }
    }

    fun createYandexBackup() {
        if (syncState.value.running) return
        val current = uiState.value.settings ?: AppSettingsEntity()
        syncState.value = DataSyncState(running = true, stage = "Подготавливаем резервную копию…")
        viewModelScope.launch {
            runCatching { yandexDiskSyncManager.createBackup(current.yandexRootPath, ::updateYandexSyncProgress) }
                .onSuccess { name ->
                    val resultMessage = "Резервная копия $name создана на Яндекс Диске"
                    repository.updateSettings(current.copy(lastSyncMessage = resultMessage))
                    syncState.value = DataSyncState(stage = resultMessage, transferredBytes = 1, totalBytes = 1)
                    message.value = resultMessage
                }
                .onFailure {
                    val errorMessage = "Ошибка резервной копии: ${it.message ?: "Яндекс Диск недоступен"}"
                    repository.updateSettings(current.copy(lastSyncMessage = errorMessage))
                    syncState.value = DataSyncState(stage = errorMessage)
                    message.value = errorMessage
                }
        }
    }

    private fun updateYandexSyncProgress(progress: YandexSyncProgress) {
        val stage = when (progress.stage) {
            YandexSyncStage.PREPARING -> "Проверяем папки на Яндекс Диске…"
            YandexSyncStage.UPLOADING_REPORT -> "Загружаем актуальный отчёт…"
            YandexSyncStage.CHECKING_PLANS -> "Проверяем входящие планы…"
            YandexSyncStage.DOWNLOADING_PLAN -> "Загружаем найденный план…"
            YandexSyncStage.UPLOADING_BACKUP -> "Загружаем резервную копию…"
        }
        syncState.value = DataSyncState(
            running = true,
            stage = stage,
            transferredBytes = progress.transferredBytes,
            totalBytes = progress.totalBytes,
        )
    }

    fun selectSharedFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val name = sharedFolderSyncManager.initializeFolder(uri)
                val current = uiState.value.settings ?: AppSettingsEntity()
                repository.updateSettings(
                    current.copy(
                        sharedFolderUri = uri.toString(),
                        sharedFolderName = name,
                        lastSyncMessage = "Папка подключена",
                    ),
                )
                name
            }.onSuccess { message.value = "Общая папка «$it» подключена" }
                .onFailure { message.value = it.message ?: "Не удалось подключить папку" }
        }
    }

    fun syncSharedFolder() {
        val current = uiState.value.settings ?: return
        val uri = current.sharedFolderUri?.let(Uri::parse) ?: run {
            message.value = "Сначала выберите общую папку"
            return
        }
        viewModelScope.launch {
            repository.updateSettings(current.copy(lastSyncMessage = "Синхронизация…"))
            runCatching { sharedFolderSyncManager.sync(uri) }
                .onSuccess { result ->
                    pendingSharedPlanName = result.pendingPlanName
                    pendingSharedPlanJson = result.pendingPlanJson
                    repository.updateSettings(
                        current.copy(
                            lastSyncAtEpochMillis = System.currentTimeMillis(),
                            lastSyncMessage = result.message,
                        ),
                    )
                    result.pendingPlanJson?.let(::previewImport)
                    message.value = result.message
                }
                .onFailure {
                    val text = it.message ?: "Ошибка синхронизации"
                    repository.updateSettings(current.copy(lastSyncMessage = text))
                    message.value = text
                }
        }
    }

    fun createSharedBackup() {
        val uri = uiState.value.settings?.sharedFolderUri?.let(Uri::parse) ?: run {
            message.value = "Сначала выберите общую папку"
            return
        }
        viewModelScope.launch {
            runCatching { sharedFolderSyncManager.createBackup(uri) }
                .onSuccess { message.value = "Резервная копия $it создана" }
                .onFailure { message.value = it.message ?: "Не удалось создать копию" }
        }
    }

    fun previewBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.previewBackup(sharedFolderSyncManager.readDocument(uri))
            }.onSuccess { backupPreview.value = it }
                .onFailure { message.value = "Ошибка резервной копии: ${it.message}" }
        }
    }

    fun applyBackup() {
        val preview = backupPreview.value ?: return
        viewModelScope.launch {
            runCatching { repository.applyBackup(preview) }
                .onSuccess {
                    backupPreview.value = null
                    message.value = "Резервная копия восстановлена; локальная история сохранена"
                }
                .onFailure { message.value = it.message ?: "Копия не восстановлена" }
        }
    }

    fun dismissBackup() {
        backupPreview.value = null
    }

    fun completeSession(id: String, rpe: Int, notes: String, actualDurationSeconds: Int) {
        viewModelScope.launch {
            repository.completeSession(id, rpe, notes, actualDurationSeconds)
            message.value = "Тренировка завершена"
            automaticSyncIfEnabled()
        }
    }

    fun completeCorePractice() {
        viewModelScope.launch {
            repository.completeCorePractice()
            message.value = "Core и осанка: практика отмечена"
            automaticSyncIfEnabled()
        }
    }

    fun undoCorePractice() {
        viewModelScope.launch {
            repository.undoCorePractice()
            message.value = "Отметка core и осанки снята"
            automaticSyncIfEnabled()
        }
    }

    fun skipSession(id: String, reason: String) {
        viewModelScope.launch {
            runCatching { repository.skipSession(id, reason) }
                .onSuccess { message.value = "Тренировка пропущена" }
                .onFailure { message.value = it.message ?: "Не удалось пропустить тренировку" }
        }
    }

    fun restoreSkippedSession(id: String) {
        viewModelScope.launch {
            runCatching { repository.restoreSkippedSession(id) }
                .onSuccess { message.value = "Тренировка возвращена в план" }
                .onFailure { message.value = it.message ?: "Не удалось вернуть тренировку" }
        }
    }

    fun reopenCompletedSession(id: String, mode: ReopenCompletedMode) {
        viewModelScope.launch {
            runCatching { repository.reopenCompletedSession(id, mode) }
                .onSuccess {
                    workoutExecutionStore.clear(id)
                    message.value = when (mode) {
                        ReopenCompletedMode.CONTINUE -> "Тренировка возвращена к последнему этапу"
                        ReopenCompletedMode.START_OVER -> "Тренировка возвращена — начните заново"
                    }
                    automaticSyncIfEnabled()
                }
                .onFailure { message.value = it.message ?: "Не удалось исправить завершение" }
        }
    }

    fun steps(session: TrainingSessionEntity) = repository.decodeSteps(session)

    fun exportReport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportReport() }
                .onSuccess(onReady)
                .onFailure { message.value = it.message ?: "Не удалось сформировать отчёт" }
        }
    }

    fun previewImport(rawJson: String) {
        viewModelScope.launch {
            runCatching { repository.previewPlan(rawJson) }
                .onSuccess { importPreview.value = it }
                .onFailure { message.value = "Ошибка импорта: ${it.message}" }
        }
    }

    fun applyImport() {
        val preview = importPreview.value ?: return
        viewModelScope.launch {
            runCatching { repository.applyPlan(preview) }
                .onSuccess {
                    importPreview.value = null
                    message.value = "Новый план применён"
                    val name = pendingSharedPlanName
                    val raw = pendingSharedPlanJson
                    val folder = uiState.value.settings?.sharedFolderUri?.let(Uri::parse)
                    if (name != null && raw != null) {
                        if (pendingPlanFromYandex) {
                            val root = uiState.value.settings?.yandexRootPath ?: AppSettingsEntity().yandexRootPath
                            runCatching { yandexDiskSyncManager.archiveAppliedPlan(root, name, raw) }
                        } else if (folder != null) {
                            runCatching { sharedFolderSyncManager.archiveAppliedPlan(folder, name, raw) }
                        }
                    }
                    pendingSharedPlanName = null
                    pendingSharedPlanJson = null
                    pendingPlanFromYandex = false
                }
                .onFailure { message.value = it.message ?: "План не применён" }
        }
    }

    fun dismissImport() {
        importPreview.value = null
    }

    fun proposeNextBaseBlock() {
        if (uiState.value.reviewCheckpoints.any {
                it.status != ru.yakovenko.mountainform.data.ReviewStatus.RESOLVED && it.reason.contains("боль", ignoreCase = true)
            }) {
            message.value = "После отметки боли новый блок не формируется автоматически — сначала разберите контрольный отчёт"
            return
        }
        if (uiState.value.readinessDecision.level == ReadinessLevel.RED) {
            message.value = "При красном статусе новый блок не формируется; сначала разберите симптомы и состояние"
            return
        }
        viewModelScope.launch {
            runCatching { repository.proposeNextBaseBlock() }
                .onSuccess { importPreview.value = it }
                .onFailure { message.value = it.message ?: "Не удалось сформировать следующий блок" }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private suspend fun automaticSyncIfEnabled() {
        val current = uiState.value.settings ?: return
        if (current.automaticSync && current.yandexSyncEnabled && secureTokenStore.hasToken()) {
            YandexSyncWorker.enqueue(reminderScheduler.context)
            return
        }
        val uri = current.sharedFolderUri?.let(Uri::parse) ?: return
        if (!current.automaticSync) return
        runCatching { sharedFolderSyncManager.sync(uri) }
            .onSuccess { result ->
                pendingSharedPlanName = result.pendingPlanName
                pendingSharedPlanJson = result.pendingPlanJson
                repository.updateSettings(
                    current.copy(
                        lastSyncAtEpochMillis = System.currentTimeMillis(),
                        lastSyncMessage = result.message,
                    ),
                )
                result.pendingPlanJson?.let { raw ->
                    runCatching { repository.previewPlan(raw) }.onSuccess { importPreview.value = it }
                }
            }
            .onFailure {
                repository.updateSettings(current.copy(lastSyncMessage = it.message ?: "Ошибка автообмена"))
            }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            val days = uiState.value.settings?.healthWindowDays ?: 30
            healthSummary.value = healthConnectManager.readSummary(days)
            runCatching { healthConnectManager.readActivities(days) }
                .onSuccess { repository.upsertImportedActivities(it) }
        }
    }

    fun setHealthWindow(days: Int) {
        if (days !in setOf(7, 30, 90)) return
        healthSummary.value = healthSummary.value.copy(windowDays = days)
        viewModelScope.launch {
            val current = uiState.value.settings ?: AppSettingsEntity()
            repository.updateSettings(current.copy(healthWindowDays = days))
            healthSummary.value = healthConnectManager.readSummary(days)
            runCatching { healthConnectManager.readActivities(days) }
                .onSuccess { repository.upsertImportedActivities(it) }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch { refreshUpdateState(showErrors = true) }
    }

    private suspend fun refreshUpdateState(showErrors: Boolean) {
        updateState.value = updateState.value.copy(
            operation = UpdateOperation.CHECKING,
            message = if (showErrors) "Проверяем обновления…" else updateState.value.message,
        )
        runCatching { appUpdateManager.check() }
            .onSuccess { release ->
                val downloadedFile = release?.let { appUpdateManager.findDownloaded(it) }
                updateState.value = UpdateState(
                    release = release,
                    downloadedFile = downloadedFile,
                    downloadedBytes = downloadedFile?.length() ?: 0,
                    totalBytes = downloadedFile?.length(),
                    message = if (release == null) {
                        if (ru.yakovenko.mountainform.BuildConfig.UPDATE_MANIFEST_URL.isBlank()) {
                            "Канал обновлений будет подключён при первой публикации"
                        } else {
                            "Установлена актуальная версия"
                        }
                    } else if (downloadedFile != null) {
                        "APK ${release.versionName} уже скачан, проверен и готов к установке"
                    } else {
                        "Доступна версия ${release.versionName}"
                    },
                )
            }
            .onFailure { error ->
                updateState.value = updateState.value.copy(
                    operation = UpdateOperation.IDLE,
                    message = if (showErrors) "Ошибка проверки: ${error.message}" else updateState.value.message,
                )
            }
    }

    fun downloadUpdate() {
        val release = updateState.value.release ?: return
        viewModelScope.launch {
            updateState.value = updateState.value.copy(
                operation = UpdateOperation.DOWNLOADING,
                downloadedFile = null,
                downloadedBytes = 0,
                totalBytes = null,
                message = "Скачиваем подписанный APK…",
            )
            runCatching {
                appUpdateManager.download(
                    release = release,
                    onProgress = { downloadedBytes, totalBytes ->
                        updateState.value = updateState.value.copy(
                            operation = UpdateOperation.DOWNLOADING,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                        )
                    },
                    onVerifying = {
                        updateState.value = updateState.value.copy(
                            operation = UpdateOperation.VERIFYING,
                            message = "Проверяем подпись и контрольную сумму APK…",
                        )
                    },
                )
            }
                .onSuccess { file ->
                    updateState.value = updateState.value.copy(
                        operation = UpdateOperation.IDLE,
                        downloadedFile = file,
                        downloadedBytes = file.length(),
                        totalBytes = file.length(),
                        message = "APK проверен и готов к установке",
                    )
                }
                .onFailure {
                    updateState.value = updateState.value.copy(
                        operation = UpdateOperation.IDLE,
                        downloadedBytes = 0,
                        totalBytes = null,
                        message = "Ошибка скачивания: ${it.message}",
                    )
                }
        }
    }

    fun installUpdate() {
        val file = updateState.value.downloadedFile ?: return
        val launched = appUpdateManager.install(file)
        if (!launched) message.value = "Разрешите установку из этого источника и повторите действие"
    }

    class Factory(
        private val repository: MountainFormRepository,
        private val healthConnectManager: HealthConnectManager,
        private val appUpdateManager: AppUpdateManager,
        private val reminderScheduler: ReminderScheduler,
        private val sharedFolderSyncManager: SharedFolderSyncManager,
        private val yandexDiskSyncManager: YandexDiskSyncManager,
        private val secureTokenStore: SecureTokenStore,
        private val fitActivityImporter: FitActivityImporter,
        private val workoutExecutionStore: WorkoutExecutionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(
                repository,
                healthConnectManager,
                appUpdateManager,
                reminderScheduler,
                sharedFolderSyncManager,
                yandexDiskSyncManager,
                secureTokenStore,
                fitActivityImporter,
                workoutExecutionStore,
            ) as T
    }
}
