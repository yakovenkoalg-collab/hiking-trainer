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
import ru.yakovenko.mountainform.data.RescheduleEventEntity
import ru.yakovenko.mountainform.data.SessionStepLogEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.UserProfileEntity
import ru.yakovenko.mountainform.domain.ReadinessDecision
import ru.yakovenko.mountainform.domain.ReadinessLevel
import ru.yakovenko.mountainform.domain.TrainingSafety
import ru.yakovenko.mountainform.health.HealthConnectManager
import ru.yakovenko.mountainform.health.HealthSummary
import ru.yakovenko.mountainform.reminders.ReminderScheduler
import ru.yakovenko.mountainform.sync.SharedFolderSyncManager
import ru.yakovenko.mountainform.update.AppUpdateManager
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

class AppViewModel(
    private val repository: MountainFormRepository,
    private val healthConnectManager: HealthConnectManager,
    private val appUpdateManager: AppUpdateManager,
    private val reminderScheduler: ReminderScheduler,
    private val sharedFolderSyncManager: SharedFolderSyncManager,
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

    val uiState: StateFlow<AppUiState> = combine(trainingState, wellnessState, extendedState) { training, wellness, extended ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    val healthSummary = MutableStateFlow(HealthSummary())
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val backupPreview = MutableStateFlow<BackupPreview?>(null)
    val message = MutableStateFlow<String?>(null)
    val updateState = MutableStateFlow(UpdateState())
    private var pendingSharedPlanName: String? = null
    private var pendingSharedPlanJson: String? = null

    init {
        viewModelScope.launch {
            repository.initialize()
            refreshHealth()
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
            message.value = "Настройки сохранены"
        }
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

    fun completeSession(id: String, rpe: Int, notes: String) {
        viewModelScope.launch {
            repository.completeSession(id, rpe, notes)
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

    fun skipSession(id: String, reason: String) {
        viewModelScope.launch {
            repository.skipSession(id, reason)
            message.value = "Тренировка пропущена"
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
                    if (name != null && raw != null && folder != null) {
                        runCatching { sharedFolderSyncManager.archiveAppliedPlan(folder, name, raw) }
                    }
                    pendingSharedPlanName = null
                    pendingSharedPlanJson = null
                }
                .onFailure { message.value = it.message ?: "План не применён" }
        }
    }

    fun dismissImport() {
        importPreview.value = null
    }

    fun proposeNextBaseBlock() {
        if (uiState.value.readinessDecision.level != ReadinessLevel.GREEN) {
            message.value = "Перед новым блоком отметьте хорошее самочувствие; при боли нагрузку не увеличиваем"
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
            healthSummary.value = healthConnectManager.readSummary(uiState.value.settings?.healthWindowDays ?: 30)
        }
    }

    fun setHealthWindow(days: Int) {
        if (days !in setOf(7, 30, 90)) return
        viewModelScope.launch {
            val current = uiState.value.settings ?: AppSettingsEntity()
            repository.updateSettings(current.copy(healthWindowDays = days))
            healthSummary.value = healthConnectManager.readSummary(days)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            updateState.value = updateState.value.copy(checking = true, message = "Проверяем обновления…")
            runCatching { appUpdateManager.check() }
                .onSuccess { release ->
                    updateState.value = UpdateState(
                        release = release,
                        message = if (release == null) {
                            if (ru.yakovenko.mountainform.BuildConfig.UPDATE_MANIFEST_URL.isBlank())
                                "Канал обновлений будет подключён при первой публикации"
                            else "Установлена актуальная версия"
                        } else "Доступна версия ${release.versionName}",
                    )
                }
                .onFailure { updateState.value = UpdateState(message = "Ошибка проверки: ${it.message}") }
        }
    }

    fun downloadUpdate() {
        val release = updateState.value.release ?: return
        viewModelScope.launch {
            updateState.value = updateState.value.copy(checking = true, message = "Скачиваем подписанный APK…")
            runCatching { appUpdateManager.download(release) }
                .onSuccess { file ->
                    updateState.value = updateState.value.copy(checking = false, downloadedFile = file, message = "APK проверен и готов к установке")
                }
                .onFailure { updateState.value = updateState.value.copy(checking = false, message = "Ошибка скачивания: ${it.message}") }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(repository, healthConnectManager, appUpdateManager, reminderScheduler, sharedFolderSyncManager) as T
    }
}
