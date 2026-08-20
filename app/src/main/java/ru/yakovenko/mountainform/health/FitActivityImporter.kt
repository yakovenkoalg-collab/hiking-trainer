package ru.yakovenko.mountainform.health

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.garmin.fit.Decode
import com.garmin.fit.LapMesg
import com.garmin.fit.LapMesgListener
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.MesgNum
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import com.garmin.fit.TimeInZoneMesg
import com.garmin.fit.TimeInZoneMesgListener
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ActivityLapSummary
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class FitActivityImporter(private val context: Context) {
    fun import(uri: Uri): List<ImportedActivityEntity> {
        val resolver = context.contentResolver
        val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "activity.fit"
        val sourceBytes = resolver.openInputStream(uri)?.use { readLimited(it, MAX_ARCHIVE_BYTES) }
            ?: error("Не удалось открыть файл")
        return inputFiles(fileName, sourceBytes).flatMap { (entryName, bytes) -> decodeFit(entryName, bytes) }
    }

    private fun decodeFit(fileName: String, bytes: ByteArray): List<ImportedActivityEntity> {
        val valid = Decode().checkFileIntegrity(ByteArrayInputStream(bytes))
        require(valid) { "Файл повреждён или не является Garmin FIT" }

        val sessions = mutableListOf<SessionMesg>()
        val laps = mutableListOf<LapMesg>()
        val timeInZones = mutableListOf<TimeInZoneMesg>()
        val broadcaster = MesgBroadcaster(Decode())
        broadcaster.addListener(SessionMesgListener { sessions += SessionMesg(it) })
        broadcaster.addListener(LapMesgListener { laps += LapMesg(it) })
        broadcaster.addListener(TimeInZoneMesgListener { timeInZones += TimeInZoneMesg(it) })
        ByteArrayInputStream(bytes).use(broadcaster::run)
        require(sessions.isNotEmpty()) { "В FIT-файле нет итоговой записи тренировки" }

        val now = System.currentTimeMillis()
        return sessions.mapIndexed { index, session ->
            val durationSeconds = (session.totalTimerTime ?: session.totalElapsedTime ?: 0f).toLong()
            val start = session.startTime?.instant
                ?: session.timestamp?.instant?.minusSeconds(durationSeconds)
                ?: error("В FIT-файле нет времени тренировки")
            val end = session.timestamp?.instant ?: start.plusSeconds(durationSeconds)
            val sourceId = sha256("$fileName|${start.toEpochMilli()}|$index|${session.sport}")
            val zoneDetails = timeInZones.firstOrNull {
                it.referenceMesg == MesgNum.SESSION && it.referenceIndex == session.messageIndex
            } ?: timeInZones.firstOrNull { it.referenceMesg == MesgNum.SESSION }
            val sessionLaps = lapsForSession(session, laps).mapIndexed { lapIndex, lap ->
                ActivityLapSummary(
                    index = lapIndex + 1,
                    durationSeconds = (lap.totalTimerTime ?: lap.totalElapsedTime ?: 0f).toLong(),
                    distanceMeters = lap.totalDistance?.toDouble(),
                    ascentMeters = lap.totalAscent?.toDouble(),
                    descentMeters = lap.totalDescent?.toDouble(),
                    averageHeartRate = lap.avgHeartRate?.toDouble(),
                    maxHeartRate = lap.maxHeartRate?.toDouble(),
                    averageCadence = actualCadence(lap.avgRunningCadence, lap.avgFractionalCadence, session.sport, lap.avgCadence),
                    averagePowerWatts = lap.avgPower?.toDouble(),
                )
            }
            ImportedActivityEntity(
                id = "fit-$sourceId",
                sourceRecordId = sourceId,
                sourceType = ActivitySourceType.FIT,
                sourcePackage = "com.garmin.fit",
                title = session.sportProfileName?.takeIf { it.isNotBlank() }
                    ?: sportLabel(session.sport),
                activityType = sportLabel(session.sport),
                startAtEpochMillis = start.toEpochMilli(),
                endAtEpochMillis = end.toEpochMilli(),
                durationSeconds = durationSeconds,
                distanceMeters = session.totalDistance?.toDouble(),
                elevationMeters = session.totalAscent?.toDouble(),
                descentMeters = session.totalDescent?.toDouble(),
                caloriesKcal = session.totalCalories?.toDouble(),
                averageHeartRate = session.avgHeartRate?.toDouble(),
                maxHeartRate = session.maxHeartRate?.toDouble(),
                averageCadence = actualCadence(
                    session.avgRunningCadence,
                    session.avgFractionalCadence,
                    session.sport,
                    session.avgCadence,
                ),
                averagePowerWatts = session.avgPower?.toDouble(),
                aerobicTrainingEffect = session.totalTrainingEffect?.toDouble(),
                anaerobicTrainingEffect = session.totalAnaerobicTrainingEffect?.toDouble(),
                trainingLoad = session.trainingLoadPeak?.toDouble(),
                configuredMaxHeartRate = zoneDetails?.maxHeartRate?.toDouble(),
                configuredRestingHeartRate = zoneDetails?.restingHeartRate?.toDouble(),
                thresholdHeartRate = zoneDetails?.thresholdHeartRate?.toDouble(),
                heartRateZoneBoundariesJson = json.encodeToString(
                    zoneDetails?.hrZoneHighBoundary.orEmpty().mapNotNull { it?.toDouble() },
                ),
                timeInHeartRateZonesJson = json.encodeToString(
                    session.timeInHrZone.orEmpty().takeIf { it.isNotEmpty() }
                        ?.map { it?.toDouble() ?: 0.0 }
                        ?: zoneDetails?.timeInHrZone.orEmpty().map { it?.toDouble() ?: 0.0 },
                ),
                averageVerticalOscillationMm = session.avgVerticalOscillation?.toDouble(),
                averageVerticalRatioPercent = session.avgVerticalRatio?.toDouble(),
                averageGroundContactTimeMs = session.avgStanceTime?.toDouble(),
                averageStepLengthMm = session.avgStepLength?.toDouble(),
                lapsJson = json.encodeToString(sessionLaps),
                importedAtEpochMillis = now,
                rawFileName = fileName,
            )
        }
    }

    private fun lapsForSession(session: SessionMesg, laps: List<LapMesg>): List<LapMesg> {
        val first = session.firstLapIndex ?: return laps
        val count = session.numLaps ?: return laps
        return laps.filter { lap -> lap.messageIndex?.let { it in first until (first + count) } == true }
            .ifEmpty { laps.drop(first).take(count) }
    }

    private fun sportLabel(sport: Sport?): String = when (sport) {
        Sport.RUNNING -> "Бег"
        Sport.WALKING -> "Ходьба"
        Sport.HIKING, Sport.MOUNTAINEERING -> "Поход"
        Sport.CYCLING, Sport.E_BIKING -> "Велосипед"
        Sport.TRAINING, Sport.FITNESS_EQUIPMENT -> "Силовая тренировка"
        else -> sport?.name?.lowercase()?.replace('_', ' ') ?: "Garmin FIT"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 50 * 1024 * 1024
        private const val MAX_FIT_BYTES = 25 * 1024 * 1024
        private const val MAX_FIT_FILES = 100
        private val json = Json { encodeDefaults = true }

        internal fun inputFiles(fileName: String, bytes: ByteArray): List<Pair<String, ByteArray>> {
            if (!fileName.endsWith(".zip", ignoreCase = true)) return listOf(fileName to bytes)
            val result = mutableListOf<Pair<String, ByteArray>>()
            var totalUncompressedBytes = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".fit", ignoreCase = true)) {
                        require(result.size < MAX_FIT_FILES) { "В архиве слишком много FIT-файлов" }
                        val fitBytes = readLimited(zip, MAX_FIT_BYTES)
                        totalUncompressedBytes += fitBytes.size
                        require(totalUncompressedBytes <= MAX_ARCHIVE_BYTES) { "FIT-архив слишком большой" }
                        result += entry.name.substringAfterLast('/').ifBlank { "activity.fit" } to fitBytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(result.isNotEmpty()) { "В ZIP-архиве нет файлов .FIT" }
            return result
        }

        private fun readLimited(input: InputStream, limitBytes: Int): ByteArray {
            val output = ByteArrayOutputStream(minOf(limitBytes, 64 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limitBytes) { "Файл слишком большой" }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        internal fun actualCadence(
            runningCadence: Short?,
            fractionalCadence: Float?,
            sport: Sport?,
            cadence: Short?,
        ): Double? = when {
            sport == Sport.RUNNING && runningCadence != null ->
                (runningCadence.toDouble() + (fractionalCadence?.toDouble() ?: 0.0)) * 2.0
            cadence != null -> cadence.toDouble() + (fractionalCadence?.toDouble() ?: 0.0)
            else -> null
        }
    }
}
