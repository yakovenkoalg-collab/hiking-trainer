package ru.yakovenko.mountainform.health

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import ru.yakovenko.mountainform.data.ActivitySourceType
import ru.yakovenko.mountainform.data.ImportedActivityEntity
import java.security.MessageDigest

class FitActivityImporter(private val context: Context) {
    fun import(uri: Uri): List<ImportedActivityEntity> {
        val resolver = context.contentResolver
        val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "activity.fit"
        val valid = resolver.openInputStream(uri)?.use { Decode().checkFileIntegrity(it) } ?: false
        require(valid) { "Файл повреждён или не является Garmin FIT" }

        val sessions = mutableListOf<SessionMesg>()
        val broadcaster = MesgBroadcaster(Decode())
        broadcaster.addListener(SessionMesgListener { sessions += SessionMesg(it) })
        resolver.openInputStream(uri)?.use(broadcaster::run) ?: error("Не удалось открыть FIT-файл")
        require(sessions.isNotEmpty()) { "В FIT-файле нет итоговой записи тренировки" }

        val now = System.currentTimeMillis()
        return sessions.mapIndexed { index, session ->
            val durationSeconds = (session.totalTimerTime ?: session.totalElapsedTime ?: 0f).toLong()
            val start = session.startTime?.instant
                ?: session.timestamp?.instant?.minusSeconds(durationSeconds)
                ?: error("В FIT-файле нет времени тренировки")
            val end = session.timestamp?.instant ?: start.plusSeconds(durationSeconds)
            val sourceId = sha256("$fileName|${start.toEpochMilli()}|$index|${session.sport}")
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
                caloriesKcal = session.totalCalories?.toDouble(),
                averageHeartRate = session.avgHeartRate?.toDouble(),
                maxHeartRate = session.maxHeartRate?.toDouble(),
                averageCadence = session.avgCadence?.toDouble(),
                averagePowerWatts = session.avgPower?.toDouble(),
                importedAtEpochMillis = now,
                rawFileName = fileName,
            )
        }
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
}
