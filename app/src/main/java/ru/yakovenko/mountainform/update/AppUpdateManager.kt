package ru.yakovenko.mountainform.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.yakovenko.mountainform.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable
data class ReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

enum class UpdateOperation {
    IDLE,
    CHECKING,
    DOWNLOADING,
    VERIFYING,
}

data class UpdateState(
    val operation: UpdateOperation = UpdateOperation.IDLE,
    val release: ReleaseManifest? = null,
    val downloadedFile: File? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val message: String = if (BuildConfig.UPDATE_MANIFEST_URL.isBlank()) "Канал обновлений будет подключён при первой публикации" else "",
) {
    val busy: Boolean get() = operation != UpdateOperation.IDLE
    val downloadProgress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let {
            (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

class AppUpdateManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = false }

    suspend fun check(): ReleaseManifest? = withContext(Dispatchers.IO) {
        if (BuildConfig.UPDATE_MANIFEST_URL.isBlank()) return@withContext null
        val connection = URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-cache")
        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val manifest = connection.inputStream.bufferedReader().use { json.decodeFromString<ReleaseManifest>(it.readText()) }
            manifest.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun findDownloaded(release: ReleaseManifest): File? = withContext(Dispatchers.IO) {
        updateFile(release).takeIf { it.isFile && sha256(it).equals(release.sha256, ignoreCase = true) }
    }

    suspend fun download(
        release: ReleaseManifest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        onVerifying: () -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val destination = updateFile(release)
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            var downloadedBytes = 0L
            onProgress(downloadedBytes, totalBytes)
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
            onVerifying()
            val actual = sha256(destination)
            require(actual.equals(release.sha256, ignoreCase = true)) { "Контрольная сумма APK не совпадает" }
            destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun updateFile(release: ReleaseManifest): File {
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        return File(directory, "mountain-form-${release.versionName}.apk")
    }

    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
