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
    }

    suspend fun saveReadiness(check: ReadinessCheckEntity) = dao.upsertReadiness(check)

    suspend fun saveBodyMetric(metric: BodyMetricEntity) = dao.upsertBodyMetric(metric)

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
