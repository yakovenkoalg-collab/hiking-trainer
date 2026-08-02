package ru.yakovenko.mountainform.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class ImportPreview(
    val plan: PlanEnvelope,
    val added: Int,
    val updated: Int,
    val conflicts: List<String>,
)

class MountainFormRepository(
    private val dao: MountainFormDao,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        explicitNulls = false
    },
) {
    val profile: Flow<UserProfileEntity?> = dao.observeProfile()
    val goals: Flow<List<GoalEventEntity>> = dao.observeGoals()
    val sessions: Flow<List<TrainingSessionEntity>> = dao.observeSessions()
    val readiness: Flow<List<ReadinessCheckEntity>> = dao.observeReadiness()
    val bodyMetrics: Flow<List<BodyMetricEntity>> = dao.observeBodyMetrics()
    val practices: Flow<List<PracticeLogEntity>> = dao.observePractices()
    val exerciseCatalog: Flow<List<ExerciseCatalogEntity>> = dao.observeExerciseCatalog()
    val settings: Flow<AppSettingsEntity?> = dao.observeSettings()
    val rescheduleEvents: Flow<List<RescheduleEventEntity>> = dao.observeRescheduleEvents()
    val postureAssessments: Flow<List<PostureAssessmentEntity>> = dao.observePostureAssessments()
    val stepLogs: Flow<List<SessionStepLogEntity>> = dao.observeStepLogs()

    suspend fun initialize() {
        val now = System.currentTimeMillis()
        if (dao.getProfile() == null) {
            dao.upsertProfile(
                UserProfileEntity(
                    age = 41,
                    heightCm = 183,
                    weightKg = 75.0,
                    preferredDays = "Вторник, пятница, воскресенье",
                    currentPhase = "Восстановление после похода",
                    shoulderRestrictionActive = true,
                    kneeObservationActive = true,
                    updatedAtEpochMillis = now,
                ),
            )
            dao.upsertGoal(
                GoalEventEntity(
                    id = "mountain-readiness",
                    type = GoalType.MOUNTAIN,
                    title = "Готовность к сложным горным походам",
                    targetEpochDay = null,
                    distanceKm = null,
                    priority = 10,
                    status = "ACTIVE",
                    notes = "7–15 дней, до 2000 м набора в сутки, высота до 6000 м",
                ),
            )
            dao.upsertGoal(
                GoalEventEntity(
                    id = "spring-road-race-2027",
                    type = GoalType.RUNNING,
                    title = "Весенний старт: 21,1 или 42,2 км",
                    targetEpochDay = null,
                    distanceKm = 21.1,
                    priority = 8,
                    status = "BASELINE_PENDING",
                    notes = "Предварительно полумарафон; марафон решается после беговой базы",
                ),
            )
        }
        if (dao.sessionCount() == 0) {
            dao.upsertSessions(seedSessions(LocalDate.now()))
        }
        if (dao.getSettings() == null) {
            dao.upsertSettings(AppSettingsEntity())
        }
        if (dao.getExerciseCatalog().isEmpty()) {
            dao.upsertExerciseCatalog(seedExerciseCatalog(json))
        }
    }

    suspend fun saveReadiness(check: ReadinessCheckEntity) = dao.upsertReadiness(check)

    suspend fun saveBodyMetric(metric: BodyMetricEntity) = dao.upsertBodyMetric(metric)

    suspend fun updateProfile(profile: UserProfileEntity) =
        dao.upsertProfile(profile.copy(updatedAtEpochMillis = System.currentTimeMillis()))

    suspend fun updateGoal(goal: GoalEventEntity) = dao.upsertGoal(goal)

    suspend fun updateSettings(settings: AppSettingsEntity) = dao.upsertSettings(settings)

    suspend fun savePostureAssessment(assessment: PostureAssessmentEntity) =
        dao.upsertPostureAssessment(assessment)

    suspend fun setStepCompleted(sessionId: String, stepId: String, completed: Boolean) {
        dao.upsertStepLog(
            SessionStepLogEntity(
                sessionId = sessionId,
                stepId = stepId,
                completed = completed,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun observeStepLogs(sessionId: String): Flow<List<SessionStepLogEntity>> =
        dao.observeStepLogs(sessionId)

    suspend fun rescheduleSession(id: String, newEpochDay: Long, reason: String) {
        val session = dao.getSession(id) ?: return
        require(session.status == SessionStatus.PLANNED) { "Переносить можно только запланированную тренировку" }
        require(newEpochDay != session.plannedEpochDay) { "Выберите другую дату" }
        val now = System.currentTimeMillis()
        dao.rescheduleSession(
            session = session.copy(
                plannedEpochDay = newEpochDay,
                originalEpochDay = session.originalEpochDay.takeIf { it != 0L } ?: session.plannedEpochDay,
                rescheduleReason = reason.trim(),
                planVersion = session.planVersion + 1,
            ),
            event = RescheduleEventEntity(
                id = UUID.randomUUID().toString(),
                sessionId = id,
                fromEpochDay = session.plannedEpochDay,
                toEpochDay = newEpochDay,
                reason = reason.trim(),
                createdAtEpochMillis = now,
            ),
        )
    }

    suspend fun completeCorePractice(notes: String = "") {
        val epochDay = LocalDate.now().toEpochDay()
        dao.upsertPractice(
            PracticeLogEntity(
                id = "$epochDay-core-posture",
                epochDay = epochDay,
                type = "CORE_POSTURE",
                minutes = 10,
                notes = notes.trim(),
            ),
        )
    }

    suspend fun completeSession(id: String, rpe: Int, notes: String) {
        val session = dao.getSessions().firstOrNull { it.id == id } ?: return
        dao.upsertSession(
            session.copy(
                status = SessionStatus.COMPLETED,
                completedAtEpochMillis = System.currentTimeMillis(),
                actualRpe = rpe,
                completionNotes = notes.trim(),
            ),
        )
    }

    suspend fun skipSession(id: String, reason: String) {
        val session = dao.getSessions().firstOrNull { it.id == id } ?: return
        dao.upsertSession(
            session.copy(
                status = SessionStatus.SKIPPED,
                completedAtEpochMillis = System.currentTimeMillis(),
                completionNotes = reason.trim(),
            ),
        )
    }

    fun decodeSteps(session: TrainingSessionEntity): List<ExerciseStep> =
        runCatching { json.decodeFromString<List<ExerciseStep>>(session.stepsJson) }.getOrDefault(emptyList())

    suspend fun exportReport(today: LocalDate = LocalDate.now()): String {
        val start = today.minusDays(13).toEpochDay()
        val end = today.toEpochDay()
        val profile = requireNotNull(dao.getProfile())
        val report = ReportEnvelope(
            generatedAtEpochMillis = System.currentTimeMillis(),
            periodStartEpochDay = start,
            periodEndEpochDay = end,
            profile = ReportProfile(
                age = profile.age,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                currentPhase = profile.currentPhase,
                activeConstraints = buildList {
                    if (profile.shoulderRestrictionActive) add("Левое плечо: болезненное отведение")
                    if (profile.kneeObservationActive) add("Правое колено: наблюдение после длинных спусков")
                },
            ),
            goals = dao.getGoals().map {
                ReportGoal(it.type, it.title, it.targetEpochDay, it.distanceKm, it.status)
            },
            sessions = dao.getSessions().filter { it.plannedEpochDay in start..end }.map {
                ReportSession(
                    plannedEpochDay = it.plannedEpochDay,
                    title = it.title,
                    type = it.type,
                    status = it.status,
                    targetRpe = it.targetRpe,
                    actualRpe = it.actualRpe,
                    notes = it.completionNotes,
                )
            },
            readiness = dao.getReadiness().filter { it.epochDay in start..end }.map {
                ReportReadiness(
                    epochDay = it.epochDay,
                    sleep = it.sleep,
                    energy = it.energy,
                    fatigue = it.fatigue,
                    shoulderPain = it.shoulderPain,
                    kneePain = it.kneePain,
                    illness = it.illness,
                    notes = it.notes,
                )
            },
            bodyMetrics = dao.getBodyMetrics().filter { it.epochDay in start..end }.map {
                ReportBodyMetric(it.epochDay, it.weightKg, it.waistCm)
            },
        )
        return json.encodeToString(report)
    }

    suspend fun exportBackup(): String {
        val settings = dao.getSettings() ?: AppSettingsEntity()
        val backup = BackupEnvelope(
            generatedAtEpochMillis = System.currentTimeMillis(),
            profile = requireNotNull(dao.getProfile()),
            goals = dao.getGoals(),
            sessions = dao.getSessions(),
            readiness = dao.getReadiness(),
            bodyMetrics = dao.getBodyMetrics(),
            revisions = dao.getRevisions(),
            practices = dao.getPractices(),
            rescheduleEvents = dao.getRescheduleEvents(),
            stepLogs = dao.getStepLogs(),
            postureAssessments = dao.getPostureAssessments().map {
                it.copy(frontPhotoUri = null, sidePhotoUri = null, backPhotoUri = null)
            },
            preferences = settings.copy(
                sharedFolderUri = null,
                sharedFolderName = null,
                lastSyncAtEpochMillis = null,
                lastSyncMessage = "Общая папка не выбрана",
            ),
        )
        return json.encodeToString(backup)
    }

    suspend fun previewBackup(rawJson: String): BackupPreview {
        val backup = json.decodeFromString<BackupEnvelope>(rawJson)
        require(backup.schemaVersion == 1) { "Неподдерживаемая версия резервной копии" }
        val existing = dao.getSessions().associateBy { it.id }
        return BackupPreview(
            backup = backup,
            newSessions = backup.sessions.count { it.id !in existing },
            restoredHistory = backup.sessions.count {
                it.status != SessionStatus.PLANNED && existing[it.id]?.status == SessionStatus.PLANNED
            },
            preservedLocalHistory = backup.sessions.count {
                existing[it.id]?.status != null && existing.getValue(it.id).status != SessionStatus.PLANNED
            },
            readinessRecords = backup.readiness.size,
            bodyMetricRecords = backup.bodyMetrics.size,
        )
    }

    suspend fun applyBackup(preview: BackupPreview) {
        val backup = preview.backup
        val currentSessions = dao.getSessions().associateBy { it.id }
        val sessions = backup.sessions.mapNotNull { restored ->
            val current = currentSessions[restored.id]
            when {
                current == null -> restored
                current.status != SessionStatus.PLANNED -> null
                else -> restored
            }
        }
        val readinessKeys = dao.getReadiness().mapTo(mutableSetOf()) { it.epochDay }
        val bodyKeys = dao.getBodyMetrics().mapTo(mutableSetOf()) { it.epochDay }
        val revisionKeys = dao.getRevisions().mapTo(mutableSetOf()) { it.id }
        val practiceKeys = dao.getPractices().mapTo(mutableSetOf()) { it.id }
        val rescheduleKeys = dao.getRescheduleEvents().mapTo(mutableSetOf()) { it.id }
        val stepKeys = dao.getStepLogs().mapTo(mutableSetOf()) { it.sessionId to it.stepId }
        val postureKeys = dao.getPostureAssessments().mapTo(mutableSetOf()) { it.id }
        val currentSettings = dao.getSettings() ?: AppSettingsEntity()
        dao.mergeBackup(
            profile = backup.profile,
            goals = backup.goals,
            sessions = sessions,
            readiness = backup.readiness.filter { it.epochDay !in readinessKeys },
            bodyMetrics = backup.bodyMetrics.filter { it.epochDay !in bodyKeys },
            revisions = backup.revisions.filter { it.id !in revisionKeys },
            practices = backup.practices.filter { it.id !in practiceKeys },
            rescheduleEvents = backup.rescheduleEvents.filter { it.id !in rescheduleKeys },
            stepLogs = backup.stepLogs.filter { (it.sessionId to it.stepId) !in stepKeys },
            postureAssessments = backup.postureAssessments.filter { it.id !in postureKeys },
            settings = backup.preferences.copy(
                sharedFolderUri = currentSettings.sharedFolderUri,
                sharedFolderName = currentSettings.sharedFolderName,
                lastSyncAtEpochMillis = currentSettings.lastSyncAtEpochMillis,
                lastSyncMessage = currentSettings.lastSyncMessage,
            ),
        )
    }

    suspend fun previewPlan(rawJson: String): ImportPreview {
        val plan = json.decodeFromString<PlanEnvelope>(rawJson)
        require(plan.schemaVersion == 1) { "Неподдерживаемая версия схемы: ${plan.schemaVersion}" }
        require(plan.sessions.isNotEmpty()) { "План не содержит тренировок" }
        val existing = dao.getSessions().associateBy { it.id }
        val profile = requireNotNull(dao.getProfile())
        val conflicts = plan.sessions.flatMap { session ->
            session.steps.mapNotNull { step ->
                val shoulderConflict = profile.shoulderRestrictionActive &&
                    step.restrictionTags.any { it in SHOULDER_RESTRICTION_TAGS }
                if (shoulderConflict) "${session.title}: ${step.title} конфликтует с ограничением плеча" else null
            }
        }
        return ImportPreview(
            plan = plan,
            added = plan.sessions.count { it.id !in existing },
            updated = plan.sessions.count { it.id in existing && existing.getValue(it.id).status == SessionStatus.PLANNED },
            conflicts = conflicts,
        )
    }

    suspend fun applyPlan(preview: ImportPreview) {
        require(preview.conflicts.isEmpty()) { "План содержит конфликты с активными ограничениями" }
        val existing = dao.getSessions().associateBy { it.id }
        val sessionsToApply = preview.plan.sessions.mapNotNull { planned ->
            val old = existing[planned.id]
            if (old != null && old.status != SessionStatus.PLANNED) return@mapNotNull null
            TrainingSessionEntity(
                id = planned.id,
                plannedEpochDay = planned.plannedEpochDay,
                title = planned.title,
                type = planned.type,
                phase = planned.phase,
                objective = planned.objective,
                durationMinutes = planned.durationMinutes,
                targetRpe = planned.targetRpe,
                stepsJson = json.encodeToString(planned.steps),
                planVersion = (old?.planVersion ?: 0) + 1,
                originalEpochDay = old?.originalEpochDay?.takeIf { it != 0L } ?: planned.plannedEpochDay,
                rescheduleReason = old?.rescheduleReason.orEmpty(),
            )
        }
        dao.upsertSessions(sessionsToApply)
        dao.upsertRevision(
            PlanRevisionEntity(
                id = preview.plan.planId,
                importedAtEpochMillis = System.currentTimeMillis(),
                schemaVersion = preview.plan.schemaVersion,
                author = preview.plan.author,
                reason = preview.plan.reason,
                payloadJson = json.encodeToString(preview.plan),
                applied = true,
            ),
        )
    }

    suspend fun proposeNextBaseBlock(today: LocalDate = LocalDate.now()): ImportPreview {
        val existing = dao.getSessions()
        val lastDate = existing.maxOfOrNull { LocalDate.ofEpochDay(it.plannedEpochDay) } ?: today
        val firstTuesday = lastDate.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
        val profile = requireNotNull(dao.getProfile())
        val sessions = buildList {
            repeat(4) { weekIndex ->
                val tuesday = firstTuesday.plusWeeks(weekIndex.toLong())
                val friday = tuesday.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
                val sunday = tuesday.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                val deload = weekIndex == 3
                val strengthRpe = if (deload) 4 else 5
                val longMinutes = listOf(75, 85, 95, 70)[weekIndex]
                add(
                    PlanSession(
                        id = "base-strength-${tuesday.toEpochDay()}",
                        plannedEpochDay = tuesday.toEpochDay(),
                        title = if (deload) "Облегчённая силовая + core" else "Ноги, задняя цепь и core",
                        type = "STRENGTH",
                        phase = "База для гор и бега",
                        objective = "Сохранять силу ног и стабильность без отказа и без нагрузки на плечо",
                        durationMinutes = if (deload) 60 else 75,
                        targetRpe = strengthRpe,
                        steps = listOf(
                            ExerciseStep("bike", "Велотренажёр", "10 минут легко", "Постепенная разминка"),
                            ExerciseStep("box-squat", "Присед до высокой опоры", if (deload) "2 × 8, RPE 4" else "3 × 8, RPE 5", "Колено по линии стопы", restSeconds = 75),
                            ExerciseStep("hinge", "Румынская тяга с лёгким весом", if (deload) "2 × 8, RPE 4" else "3 × 8, RPE 5", "Вес близко к ногам, плечо без боли", restSeconds = 75),
                            ExerciseStep("calf", "Подъём на носки", if (deload) "2 × 12" else "3 × 12", "Медленное опускание", restSeconds = 45),
                            ExerciseStep("core", "Антиразгибание лёжа", if (deload) "2 × 6 на сторону" else "3 × 8 на сторону", "Рёбра и таз неподвижны", restSeconds = 45),
                        ),
                    ),
                )
                add(
                    PlanSession(
                        id = "base-run-${friday.toEpochDay()}",
                        plannedEpochDay = friday.toEpochDay(),
                        title = if (deload) "Лёгкий бег в разговорном темпе" else "Бег / ходьба: базовые интервалы",
                        type = "RUN",
                        phase = "База для гор и бега",
                        objective = "Постепенно создавать беговую базу для полумарафона",
                        durationMinutes = listOf(50, 55, 60, 45)[weekIndex],
                        targetRpe = if (deload) 3 else 4,
                        steps = listOf(
                            ExerciseStep("walk-warmup", "Разминка ходьбой", "10 минут", "Ровная поверхность"),
                            ExerciseStep("run-walk", "Лёгкий бег / ходьба", if (deload) "25 минут легко" else "${5 + weekIndex} × (4 мин бег + 1 мин ходьба)", "Разговорный темп; остановиться при боли"),
                            ExerciseStep("walk-cooldown", "Заминка ходьбой", "5–10 минут", "Отметить ощущения сразу и утром"),
                        ),
                    ),
                )
                add(
                    PlanSession(
                        id = "base-endurance-${sunday.toEpochDay()}",
                        plannedEpochDay = sunday.toEpochDay(),
                        title = if (deload) "Восстановительная длинная ходьба" else "Длинная ходьба с уклоном",
                        type = "AEROBIC",
                        phase = "База для гор и бега",
                        objective = if (profile.shoulderRestrictionActive) {
                            "Длительная работа без рюкзака, пока активно ограничение плеча"
                        } else {
                            "Длительная работа для гор; рюкзак не добавлять без отдельного решения"
                        },
                        durationMinutes = longMinutes,
                        targetRpe = if (deload) 3 else 4,
                        steps = listOf(
                            ExerciseStep("walk", "Ходьба или дорожка с уклоном", "$longMinutes минут, RPE ${if (deload) 3 else 4}", "Ровно, без тяжёлого рюкзака и без длинных спусков"),
                            ExerciseStep("breathing", "Дыхание и контроль рёбер", "3 × 5 циклов", "Длинный спокойный выдох"),
                        ),
                    ),
                )
            }
        }
        val envelope = PlanEnvelope(
            planId = "base-block-${firstTuesday.toEpochDay()}",
            author = "Горная форма",
            reason = "4-недельный базовый блок: сила, core, бег и длительная ходьба. Повышение нагрузки блокируется при боли или плохом самочувствии.",
            generatedAtEpochMillis = System.currentTimeMillis(),
            sessions = sessions,
        )
        return previewPlan(json.encodeToString(envelope))
    }

    private fun seedSessions(today: LocalDate): List<TrainingSessionEntity> {
        fun next(day: DayOfWeek): LocalDate = today.with(TemporalAdjusters.nextOrSame(day))
        fun session(
            id: String,
            date: LocalDate,
            title: String,
            type: String,
            duration: Int,
            rpe: Int,
            objective: String,
            steps: List<ExerciseStep>,
        ) = TrainingSessionEntity(
            id = id,
            plannedEpochDay = date.toEpochDay(),
            title = title,
            type = type,
            phase = "Восстановление после похода",
            objective = objective,
            durationMinutes = duration,
            targetRpe = rpe,
            stepsJson = json.encodeToString(steps),
        )

        val friday = next(DayOfWeek.FRIDAY)
        val sunday = next(DayOfWeek.SUNDAY)
        val tuesday = next(DayOfWeek.TUESDAY).let { if (it <= sunday) it.plusWeeks(1) else it }
        val nextFriday = friday.let { if (it <= tuesday) it.plusWeeks(1) else it }

        return listOf(
            session(
                "recovery-walk-${friday.toEpochDay()}", friday,
                "Восстановительная ходьба + мягкий core", "RECOVERY", 55, 3,
                "Вернуть ритм без накопления новой усталости",
                listOf(
                    ExerciseStep("walk", "Спокойная ходьба", "35–40 минут, RPE 2–3", "Ровная поверхность, без тяжёлого рюкзака"),
                    ExerciseStep("breathing", "Дыхание и контроль рёбер", "3 × 5 дыхательных циклов", "Лёжа, без движения плеча через боль"),
                    ExerciseStep("heel-slide", "Скольжение пяткой", "3 × 8 на сторону", "Сохранять нейтральное положение таза"),
                    ExerciseStep("mobility", "Мягкая подвижность", "8 минут", "Только безболезненный диапазон; плечо не растягивать через резкую боль"),
                ),
            ),
            session(
                "easy-aerobic-${sunday.toEpochDay()}", sunday,
                "Лёгкая аэробная работа", "AEROBIC", 65, 3,
                "Оценить восстановление через неделю после похода",
                listOf(
                    ExerciseStep("warmup", "Разминка ходьбой", "10 минут", "Постепенно поднять пульс"),
                    ExerciseStep("aerobic", "Ходьба или велотренажёр", "40 минут, разговорный темп", "Без боли в колене и плече"),
                    ExerciseStep("bridge", "Ягодичный мост", "3 × 10", "Пауза 2 секунды в верхней точке"),
                    ExerciseStep("side-core", "Боковая стабилизация без опоры на плечо", "3 × 20 секунд", "Выбрать вариант лёжа; прекратить при дискомфорте"),
                ),
            ),
            session(
                "lower-core-${tuesday.toEpochDay()}", tuesday,
                "Возвращение в зал: ноги и core", "STRENGTH", 70, 5,
                "Спокойно вернуть силовой паттерн без отказных подходов",
                listOf(
                    ExerciseStep("bike", "Велотренажёр", "10 минут легко", "Разминка"),
                    ExerciseStep("box-squat", "Присед до высокой опоры", "3 × 8, RPE 5", "Без боли; контролировать колено"),
                    ExerciseStep("hinge", "Румынская тяга с лёгким весом", "3 × 8, RPE 5", "Ровная спина, медленное опускание"),
                    ExerciseStep("calf", "Подъём на носки", "3 × 12", "Полный контролируемый диапазон"),
                    ExerciseStep("core", "Антиразгибание лёжа", "3 × 8 на сторону", "Плечи расслаблены"),
                    ExerciseStep("cooldown", "Заминка", "8 минут", "Без агрессивной растяжки плеча"),
                ),
            ),
            session(
                "run-baseline-${nextFriday.toEpochDay()}", nextFriday,
                "Беговая диагностика: легко и без цели по темпу", "RUN", 45, 4,
                "Проверить переносимость ровного лёгкого бега",
                listOf(
                    ExerciseStep("walk-warmup", "Разминка ходьбой", "10 минут", "Ровная поверхность"),
                    ExerciseStep("run-walk", "Лёгкий бег / ходьба", "6 × (3 минуты бег + 2 минуты ходьба)", "Разговорный темп; остановиться при боли"),
                    ExerciseStep("walk-cooldown", "Заминка ходьбой", "5 минут", "Отметить ощущения в колене сразу и утром"),
                ),
            ),
        ).sortedBy { it.plannedEpochDay }
    }

    companion object {
        val SHOULDER_RESTRICTION_TAGS = setOf("SHOULDER_ABDUCTION", "OVERHEAD", "HANGING", "DIPS")
    }
}
