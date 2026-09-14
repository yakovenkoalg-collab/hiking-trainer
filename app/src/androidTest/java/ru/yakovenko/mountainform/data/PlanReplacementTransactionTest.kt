package ru.yakovenko.mountainform.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.yakovenko.mountainform.domain.ProgressedHybridPlan
import java.time.LocalDate

class PlanReplacementTransactionTest {
    private lateinit var database: MountainFormDatabase
    private lateinit var dao: MountainFormDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MountainFormDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun replacementDeletesOnlyPlannedSessionsAndPreservesCompletedHistory() = runBlocking {
        val planned = session("planned-old", SessionStatus.PLANNED)
        val completed = session("completed-history", SessionStatus.COMPLETED)
        dao.upsertSessions(listOf(planned, completed))
        dao.upsertStepLogs(
            listOf(
                SessionStepLogEntity(planned.id, "step", true, 1),
                SessionStepLogEntity(completed.id, "step", true, 1),
            ),
        )
        dao.upsertSetLogs(
            listOf(
                SessionSetLogEntity(planned.id, "step", 1, 1, completed = true),
                SessionSetLogEntity(completed.id, "step", 1, 1, completed = true),
            ),
        )
        dao.upsertImportedActivity(
            ImportedActivityEntity(
                id = "activity",
                sourceRecordId = "source",
                sourceType = ActivitySourceType.FIT,
                sourcePackage = "test",
                title = "Бег",
                activityType = "Бег",
                startAtEpochMillis = 1,
                endAtEpochMillis = 2,
                durationSeconds = 1,
                linkedSessionId = planned.id,
                status = ActivityLinkStatus.LINKED,
                importedAtEpochMillis = 3,
            ),
        )

        dao.applyPlanChanges(
            sessions = listOf(session("new-plan", SessionStatus.PLANNED).copy(plannedEpochDay = 2)),
            removedPlannedSessionIds = listOf(planned.id, completed.id),
            revision = PlanRevisionEntity("revision", 4, 1, "test", "replace", "{}", true),
            resolvedCheckpoint = null,
        )

        val sessions = dao.getSessions()
        assertEquals(setOf("completed-history", "new-plan"), sessions.mapTo(mutableSetOf()) { it.id })
        assertTrue(sessions.single { it.id == completed.id }.status == SessionStatus.COMPLETED)
        assertFalse(dao.getStepLogs().any { it.sessionId == planned.id })
        assertTrue(dao.getStepLogs().any { it.sessionId == completed.id })
        assertFalse(dao.getSetLogs().any { it.sessionId == planned.id })
        assertTrue(dao.getSetLogs().any { it.sessionId == completed.id })
        val activity = dao.getImportedActivity("activity")!!
        assertNull(activity.linkedSessionId)
        assertEquals(ActivityLinkStatus.UNLINKED, activity.status)
        assertEquals("revision", dao.getRevisions().single().id)
    }

    @Test
    fun duplicatePlannedSessionOnCompletedDayIsRemovedWithOnlyItsDependentData() = runBlocking {
        val day = LocalDate.now().toEpochDay()
        val planned = session("duplicate-planned", SessionStatus.PLANNED).copy(plannedEpochDay = day)
        val completed = session("completed", SessionStatus.COMPLETED).copy(plannedEpochDay = day)
        dao.upsertSessions(listOf(planned, completed))
        dao.upsertStepLogs(
            listOf(
                SessionStepLogEntity(planned.id, "step", true, 1),
                SessionStepLogEntity(completed.id, "step", true, 1),
            ),
        )
        dao.upsertImportedActivity(activity("linked").copy(linkedSessionId = planned.id, status = ActivityLinkStatus.LINKED))

        assertEquals(1, dao.removePlannedSessionsOnCompletedDays())

        assertEquals(listOf(completed.id), dao.getSessions().map { it.id })
        assertFalse(dao.getStepLogs().any { it.sessionId == planned.id })
        assertTrue(dao.getStepLogs().any { it.sessionId == completed.id })
        assertNull(dao.getImportedActivity("linked")?.linkedSessionId)
    }

    @Test
    fun previewProtectsPastAndCompletedDaysAndClampsReplacementRange() = runBlocking {
        val today = LocalDate.of(2026, 8, 31)
        dao.upsertProfile(profile())
        dao.upsertSessions(
            listOf(
                session("past-existing", SessionStatus.PLANNED).copy(plannedEpochDay = today.minusDays(1).toEpochDay()),
                session("completed-today", SessionStatus.COMPLETED).copy(plannedEpochDay = today.toEpochDay()),
                session("future-existing", SessionStatus.PLANNED).copy(plannedEpochDay = today.plusDays(1).toEpochDay()),
            ),
        )
        val raw = Json.encodeToString(
            plan(
                "protected",
                today.minusDays(1) to "past-new",
                today to "completed-day-new",
                today.plusDays(2) to "future-new",
                replaceFrom = today.minusDays(1),
                replaceThrough = today.plusDays(2),
            ),
        )

        val preview = MountainFormRepository(dao).previewPlan(raw, today)

        assertEquals(listOf("future-new"), preview.plan.sessions.map { it.id })
        assertEquals(today.toEpochDay(), preview.plan.replacePlannedFromEpochDay)
        assertEquals(listOf("future-existing"), preview.removedSessionIds)
        assertEquals(2, preview.preservedHistory)
    }

    @Test
    fun applyRechecksCompletedDaysWhenStateChangedAfterPreview() = runBlocking {
        val today = LocalDate.now()
        val targetDay = today.plusDays(1)
        dao.upsertProfile(profile())
        val raw = Json.encodeToString(plan("race", targetDay to "future-new"))
        val repository = MountainFormRepository(dao)
        val preview = repository.previewPlan(raw, today)
        dao.upsertSession(session("completed-after-preview", SessionStatus.COMPLETED).copy(plannedEpochDay = targetDay.toEpochDay()))

        val error = runCatching { repository.applyPlan(preview) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertNull(dao.getSession("future-new"))
        assertEquals(SessionStatus.COMPLETED, dao.getSession("completed-after-preview")?.status)
    }

    @Test
    fun completedAugust30SelectsCorrectedFutureBlockInsteadOfRecreatingOldPlan() = runBlocking {
        val completedDay = LocalDate.of(2026, 8, 30)
        dao.upsertProfile(profile())
        dao.upsertSession(
            session("progress-long-${completedDay.toEpochDay()}", SessionStatus.COMPLETED)
                .copy(plannedEpochDay = completedDay.toEpochDay()),
        )
        listOf(
            LocalDate.of(2026, 9, 1) to "progress-hybrid",
            LocalDate.of(2026, 9, 3) to "progress-optional",
            LocalDate.of(2026, 9, 4) to "progress-strength",
            LocalDate.of(2026, 9, 6) to "progress-long",
            LocalDate.of(2026, 9, 8) to "progress-hybrid",
            LocalDate.of(2026, 9, 10) to "progress-optional",
            LocalDate.of(2026, 9, 11) to "progress-strength",
            LocalDate.of(2026, 9, 13) to "progress-long",
        ).forEach { (date, prefix) ->
            dao.upsertSession(
                session("$prefix-${date.toEpochDay()}", SessionStatus.PLANNED)
                    .copy(plannedEpochDay = date.toEpochDay()),
            )
        }

        val preview = MountainFormRepository(dao).proposeNextBaseBlock(LocalDate.of(2026, 8, 31))

        assertTrue(preview.plan.planId.startsWith("progressed-hybrid-v2"))
        assertTrue(preview.plan.sessions.all { it.plannedEpochDay >= LocalDate.of(2026, 8, 31).toEpochDay() })
        assertTrue(preview.plan.sessions.any { it.id.startsWith("progress-home-hybrid-") })
        assertFalse(preview.plan.sessions.any { it.id.startsWith("hybrid-long-run-") })
        assertEquals(
            setOf(
                "progress-optional-${LocalDate.of(2026, 9, 3).toEpochDay()}",
                "progress-strength-${LocalDate.of(2026, 9, 4).toEpochDay()}",
            ),
            preview.removedSessionIds.toSet(),
        )
    }

    @Test
    fun september6ProposalReplacesOnlyFutureSessionsWithTheAgreedHomeWeek() = runBlocking {
        val today = LocalDate.of(2026, 9, 6)
        val completed = session("progress-long-${today.toEpochDay()}", SessionStatus.COMPLETED)
            .copy(plannedEpochDay = today.toEpochDay())
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        dao.upsertSession(completed)
        val oldFutureIds = listOf(
            "progress-hybrid-${LocalDate.of(2026, 9, 8).toEpochDay()}" to LocalDate.of(2026, 9, 8),
            "progress-optional-${LocalDate.of(2026, 9, 10).toEpochDay()}" to LocalDate.of(2026, 9, 10),
            "progress-strength-${LocalDate.of(2026, 9, 11).toEpochDay()}-upper" to LocalDate.of(2026, 9, 11),
            "progress-long-${LocalDate.of(2026, 9, 13).toEpochDay()}" to LocalDate.of(2026, 9, 13),
        ).map { (id, date) ->
            id.also {
                dao.upsertSession(
                    session(id, SessionStatus.PLANNED).copy(plannedEpochDay = date.toEpochDay()),
                )
            }
        }

        val repository = MountainFormRepository(dao)
        val preview = repository.proposeNextBaseBlock(today)

        assertTrue(preview.plan.planId.startsWith("home-kettlebell-week-v1"))
        assertEquals(4, preview.plan.sessions.size)
        assertEquals(oldFutureIds.toSet(), preview.removedSessionIds.toSet())
        assertTrue(preview.plan.sessions.all { it.id.startsWith("home-week-") })

        repository.applyPlan(preview, today)

        assertEquals(SessionStatus.COMPLETED, dao.getSession(completed.id)?.status)
        assertTrue(oldFutureIds.all { dao.getSession(it) == null })
        assertTrue(preview.plan.sessions.all { dao.getSession(it.id) != null })
    }

    @Test
    fun appliedHomePlanNeverFallsBackToGymAndPreservesAMove() = runBlocking {
        val today = LocalDate.of(2026, 9, 10)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        val repository = MountainFormRepository(dao)
        val preview = repository.proposeNextBaseBlock(today)
        assertEquals("home-running-2026-09-10-v1", preview.plan.planId)
        repository.applyPlan(preview, today)
        val target = dao.getSessions().first()
        dao.upsertSession(target.copy(plannedEpochDay = target.plannedEpochDay + 1, rescheduleReason = "Перенос"))
        val before = dao.getSessions()
        assertTrue(runCatching { repository.proposeNextBaseBlock(today.plusDays(1)) }.isFailure)
        assertTrue(runCatching { repository.applyPlan(preview, today) }.isFailure)
        assertEquals(before, dao.getSessions())
        assertEquals(1, dao.getRevisions().size)
    }

    @Test
    fun oldHomeWeekAlreadyPresentDoesNotFallThroughToGym() = runBlocking {
        val today = LocalDate.of(2026, 9, 8)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        val repository = MountainFormRepository(dao)
        repository.applyPlan(repository.proposeNextBaseBlock(today), today)
        assertTrue(runCatching { repository.proposeNextBaseBlock(today.plusDays(1)) }.isFailure)
    }

    @Test
    fun newHomeBlockPreservesCompletedAndSkippedSessions() = runBlocking {
        val today = LocalDate.of(2026, 9, 10)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        val completed = session("completed", SessionStatus.COMPLETED).copy(plannedEpochDay = today.toEpochDay())
        val skipped = session("skipped", SessionStatus.SKIPPED).copy(plannedEpochDay = today.minusDays(1).toEpochDay())
        dao.upsertSessions(listOf(completed, skipped))
        val repository = MountainFormRepository(dao)
        val preview = repository.proposeNextBaseBlock(today)
        assertTrue(preview.plan.sessions.none { it.plannedEpochDay == today.toEpochDay() })
        repository.applyPlan(preview, today)
        assertEquals(completed, dao.getSession(completed.id))
        assertEquals(skipped, dao.getSession(skipped.id))
    }

    @Test
    fun changingShoulderRestrictionAfterPreviewBlocksApply() = runBlocking {
        val today = LocalDate.of(2026, 9, 10)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        val repository = MountainFormRepository(dao)
        val preview = repository.proposeNextBaseBlock(today)
        dao.upsertProfile(profile())
        assertTrue(runCatching { repository.applyPlan(preview, today) }.isFailure)
        assertTrue(dao.getSessions().isEmpty())
    }

    @Test
    fun newerExternalPlanIsNotOverwrittenByBundledTemplate() = runBlocking {
        val today = LocalDate.of(2026, 9, 10)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        dao.upsertRevision(PlanRevisionEntity("home-custom-v2", System.currentTimeMillis(), 1, "test", "test", "{}", true))
        assertTrue(runCatching { MountainFormRepository(dao).proposeNextBaseBlock(today) }.isFailure)
    }

    @Test
    fun newRevisionKeepsManuallyMovedWorkoutWithoutCreatingItsDuplicate() = runBlocking {
        val today = LocalDate.of(2026, 9, 10)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        val moved = session("moved", SessionStatus.PLANNED).copy(
            originalEpochDay = LocalDate.of(2026, 9, 11).toEpochDay(),
            plannedEpochDay = LocalDate.of(2026, 9, 12).toEpochDay(), rescheduleReason = "Личное расписание")
        dao.upsertSession(moved)
        val repository = MountainFormRepository(dao)
        val preview = repository.proposeNextBaseBlock(today)
        assertTrue(preview.plan.sessions.none { it.plannedEpochDay == moved.originalEpochDay })
        repository.applyPlan(preview, today)
        assertEquals(moved, dao.getSession(moved.id))
    }

    @Test
    fun loggedWorkoutCannotBeReplacedByNewPlan() = runBlocking {
        val today = LocalDate.of(2026, 9, 10)
        dao.upsertProfile(profile().copy(shoulderRestrictionActive = false))
        dao.upsertSession(session("started", SessionStatus.PLANNED).copy(plannedEpochDay = today.toEpochDay()))
        dao.upsertSetLog(SessionSetLogEntity("started", "step", 1, 1, completed = true))
        val repository = MountainFormRepository(dao)
        val preview = repository.proposeNextBaseBlock(today)
        assertTrue(preview.conflicts.isNotEmpty())
        assertTrue(runCatching { repository.applyPlan(preview, today) }.isFailure)
        assertEquals(1, dao.getSetLogs().size)
    }

    @Test
    fun correctedPlanPreviewCanRemoveAStaleFridayOnly() = runBlocking {
        val today = LocalDate.of(2026, 9, 1)
        dao.upsertProfile(profile())
        val corrected = ProgressedHybridPlan.envelope(
            includeClearedUpperBody = false,
            today = today,
            generatedAtEpochMillis = 1,
        )
        dao.upsertSessions(
            corrected.sessions.map { planned ->
                TrainingSessionEntity(
                    id = planned.id,
                    plannedEpochDay = planned.plannedEpochDay,
                    title = planned.title,
                    type = planned.type,
                    phase = planned.phase,
                    objective = planned.objective,
                    durationMinutes = planned.durationMinutes,
                    targetRpe = planned.targetRpe,
                    stepsJson = Json.encodeToString(planned.steps),
                )
            },
        )
        val staleFridayId = "progress-strength-${LocalDate.of(2026, 9, 4).toEpochDay()}"
        dao.upsertSession(
            session(staleFridayId, SessionStatus.PLANNED).copy(
                plannedEpochDay = LocalDate.of(2026, 9, 4).toEpochDay(),
            ),
        )
        val repository = MountainFormRepository(dao)

        val preview = repository.proposeNextBaseBlock(today)

        assertTrue(preview.changes.isEmpty())
        assertEquals(listOf(staleFridayId), preview.removedSessionIds)
        assertTrue(preview.conflicts.isEmpty())

        // Applying is intentionally guarded against deleting sessions that have
        // become past by the time the test suite runs. Preview behavior is the
        // stable contract covered here.
    }

    @Test
    fun reportMarksImplausibleDurationAndIncludesUpcomingPlanWithSessionIds() = runBlocking {
        val today = LocalDate.of(2026, 8, 31)
        dao.upsertProfile(profile())
        dao.upsertSessions(
            listOf(
                session("short-duration", SessionStatus.COMPLETED).copy(
                    plannedEpochDay = today.minusDays(6).toEpochDay(),
                    actualDurationSeconds = 27,
                ),
                session("upcoming", SessionStatus.PLANNED).copy(
                    plannedEpochDay = today.plusDays(3).toEpochDay(),
                ),
            ),
        )

        val report = Json.decodeFromString<ReportEnvelope>(MountainFormRepository(dao).exportReport(today))

        assertEquals(7, report.schemaVersion)
        assertEquals("SUSPECT", report.sessions.single().durationStatus)
        assertEquals("short-duration", report.sessions.single().id)
        assertEquals("upcoming", report.upcomingSessions.single().id)
        assertEquals(30, report.upcomingSessions.single().durationMinutes)
    }

    @Test
    fun oneTrainingSessionCanBeLinkedToSeveralActivities() = runBlocking {
        val target = session("target", SessionStatus.COMPLETED)
        dao.upsertSession(target)
        dao.upsertImportedActivities(listOf(activity("first"), activity("second")))

        dao.linkImportedActivity("first", target.id)
        dao.linkImportedActivity("second", target.id)

        assertEquals(ActivityLinkStatus.LINKED, dao.getImportedActivity("first")?.status)
        assertEquals(ActivityLinkStatus.LINKED, dao.getImportedActivity("second")?.status)
        assertEquals(target.id, dao.getImportedActivity("first")?.linkedSessionId)
        assertEquals(target.id, dao.getImportedActivity("second")?.linkedSessionId)
    }

    @Test
    fun replacingSessionActivitiesUnlinksOnlyRemovedRecord() = runBlocking {
        val target = session("target", SessionStatus.COMPLETED)
        dao.upsertSession(target)
        dao.upsertImportedActivities(listOf(activity("first"), activity("second"), activity("third")))
        dao.linkImportedActivity("first", target.id)
        dao.linkImportedActivity("second", target.id)

        dao.replaceSessionActivities(target.id, listOf("second", "third"))

        assertNull(dao.getImportedActivity("first")?.linkedSessionId)
        assertEquals(ActivityLinkStatus.UNLINKED, dao.getImportedActivity("first")?.status)
        assertEquals(target.id, dao.getImportedActivity("second")?.linkedSessionId)
        assertEquals(target.id, dao.getImportedActivity("third")?.linkedSessionId)
    }

    @Test
    fun skippedTrainingSessionCannotBeLinked() = runBlocking {
        val skipped = session("skipped", SessionStatus.SKIPPED)
        dao.upsertSession(skipped)
        dao.upsertImportedActivity(activity("activity"))

        val error = runCatching { dao.linkImportedActivity("activity", skipped.id) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(ActivityLinkStatus.UNLINKED, dao.getImportedActivity("activity")?.status)
    }

    @Test
    fun corePracticeMarkCanBeUndone() = runBlocking {
        val day = LocalDate.now().toEpochDay()
        dao.upsertPractice(PracticeLogEntity("$day-core-posture", day, "CORE_POSTURE", 10, ""))

        MountainFormRepository(dao).undoCorePractice()

        assertTrue(dao.getPractices().none { it.id == "$day-core-posture" })
    }

    @Test
    fun restoringSkippedSessionPreservesSetHistory() = runBlocking {
        val skipped = session("restore", SessionStatus.SKIPPED).copy(
            completedAtEpochMillis = 42,
            actualRpe = 7,
            actualDurationSeconds = 900,
            completionNotes = "Пропущена",
        )
        dao.upsertSession(skipped)
        dao.upsertSetLog(SessionSetLogEntity(skipped.id, "step", 1, 1, completed = true))

        MountainFormRepository(dao).restoreSkippedSession(skipped.id)

        val restored = requireNotNull(dao.getSession(skipped.id))
        assertEquals(SessionStatus.PLANNED, restored.status)
        assertNull(restored.completedAtEpochMillis)
        assertNull(restored.actualRpe)
        assertEquals(0, restored.actualDurationSeconds)
        assertEquals("", restored.completionNotes)
        assertTrue(dao.getSetLogs().any { it.sessionId == skipped.id })
    }

    @Test
    fun planWithAnEmptyWorkoutIsRejectedBeforeImport() = runBlocking {
        dao.upsertProfile(
            UserProfileEntity(
                age = 41,
                heightCm = 183,
                weightKg = 75.0,
                preferredDays = "вторник, пятница, воскресенье",
                currentPhase = "BASE",
                shoulderRestrictionActive = true,
                kneeObservationActive = true,
                updatedAtEpochMillis = 1,
            ),
        )
        val raw = Json.encodeToString(
            PlanEnvelope(
                planId = "empty",
                author = "test",
                reason = "test",
                generatedAtEpochMillis = 1,
                sessions = listOf(
                    PlanSession(
                        id = "empty-session",
                        plannedEpochDay = LocalDate.now().toEpochDay(),
                        title = "Пустая",
                        type = "RECOVERY",
                        phase = "BASE",
                        objective = "test",
                        durationMinutes = 10,
                        targetRpe = 2,
                        steps = emptyList(),
                    ),
                ),
            ),
        )

        val error = runCatching { MountainFormRepository(dao).previewPlan(raw) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("хотя бы одно упражнение"))
    }

    private fun activity(id: String) = ImportedActivityEntity(
        id = id,
        sourceRecordId = "source-$id",
        sourceType = ActivitySourceType.FIT,
        sourcePackage = "test",
        title = id,
        activityType = "RUNNING",
        startAtEpochMillis = 1,
        endAtEpochMillis = 2,
        durationSeconds = 1,
        importedAtEpochMillis = 3,
    )

    private fun profile() = UserProfileEntity(
        age = 41,
        heightCm = 183,
        weightKg = 75.0,
        preferredDays = "вторник, пятница, воскресенье",
        currentPhase = "BASE",
        shoulderRestrictionActive = true,
        kneeObservationActive = true,
        updatedAtEpochMillis = 1,
    )

    private fun plan(
        id: String,
        vararg sessions: Pair<LocalDate, String>,
        replaceFrom: LocalDate? = null,
        replaceThrough: LocalDate? = null,
    ) = PlanEnvelope(
        planId = id,
        author = "test",
        reason = "test",
        generatedAtEpochMillis = 1,
        replacePlannedFromEpochDay = replaceFrom?.toEpochDay(),
        replacePlannedThroughEpochDay = replaceThrough?.toEpochDay(),
        sessions = sessions.map { (date, sessionId) ->
            PlanSession(
                id = sessionId,
                plannedEpochDay = date.toEpochDay(),
                title = sessionId,
                type = "RUN",
                phase = "BASE",
                objective = "test",
                durationMinutes = 30,
                targetRpe = 3,
                steps = listOf(ExerciseStep("step", "Шаг", "1 × 1", "test")),
            )
        },
    )

    private fun session(id: String, status: String) = TrainingSessionEntity(
        id = id,
        plannedEpochDay = 1,
        title = id,
        type = "RUN",
        phase = "BASE",
        objective = "test",
        durationMinutes = 30,
        targetRpe = 3,
        stepsJson = "[]",
        status = status,
    )
}
