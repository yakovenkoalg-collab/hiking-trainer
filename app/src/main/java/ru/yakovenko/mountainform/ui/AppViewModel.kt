package ru.yakovenko.mountainform.ui

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
import ru.yakovenko.mountainform.data.GoalEventEntity
import ru.yakovenko.mountainform.data.ImportPreview
import ru.yakovenko.mountainform.data.MountainFormRepository
import ru.yakovenko.mountainform.data.PracticeLogEntity
import ru.yakovenko.mountainform.data.ReadinessCheckEntity
import ru.yakovenko.mountainform.data.TrainingSessionEntity
import ru.yakovenko.mountainform.data.UserProfileEntity
import ru.yakovenko.mountainform.domain.ReadinessDecision
import ru.yakovenko.mountainform.domain.TrainingSafety
import ru.yakovenko.mountainform.health.HealthConnectManager
import ru.yakovenko.mountainform.health.HealthSummary
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

    val uiState: StateFlow<AppUiState> = combine(trainingState, wellnessState) { training, wellness ->
        AppUiState(
            profile = training.first,
            goals = training.second,
            sessions = training.third,
            readiness = wellness.first,
            bodyMetrics = wellness.second,
            practices = wellness.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    val healthSummary = MutableStateFlow(HealthSummary())
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val message = MutableStateFlow<String?>(null)
    val updateState = MutableStateFlow(UpdateState())

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
        }
    }

    fun completeSession(id: String, rpe: Int, notes: String) {
        viewModelScope.launch {
            repository.completeSession(id, rpe, notes)
            message.value = "Тренировка завершена"
        }
    }

    fun completeCorePractice() {
        viewModelScope.launch {
            repository.completeCorePractice()
            message.value = "Core и осанка: практика отмечена"
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
                }
                .onFailure { message.value = it.message ?: "План не применён" }
        }
    }

    fun dismissImport() {
        importPreview.value = null
    }

    fun dismissMessage() {
        message.value = null
    }

    fun refreshHealth() {
        viewModelScope.launch { healthSummary.value = healthConnectManager.readSummary() }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(repository, healthConnectManager, appUpdateManager) as T
    }
}
