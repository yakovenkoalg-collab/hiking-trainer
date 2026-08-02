package ru.yakovenko.mountainform.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.yakovenko.mountainform.data.MountainFormRepository
import java.time.LocalDate

data class SharedFolderSyncResult(
    val message: String,
    val pendingPlanJson: String? = null,
    val pendingPlanName: String? = null,
)

class SharedFolderSyncManager(
    private val context: Context,
    private val repository: MountainFormRepository,
) {
    suspend fun initializeFolder(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) { "Не удалось открыть папку" }
        require(root.canRead() && root.canWrite()) { "Для общей папки нужен доступ на чтение и запись" }
        ensureLayout(root)
        root.name ?: "Общая папка"
    }

    suspend fun sync(treeUri: Uri): SharedFolderSyncResult = withContext(Dispatchers.IO) {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) { "Общая папка недоступна" }
        require(root.canRead() && root.canWrite()) { "Доступ к общей папке отозван" }
        val folders = ensureLayout(root)
        writeText(folders.reports, "current-report.json", repository.exportReport())
        val pending = folders.inbox.listFiles()
            .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
            .maxByOrNull { it.lastModified() }
        if (pending == null) {
            SharedFolderSyncResult("Отчёт обновлён, новых планов нет")
        } else {
            val raw = context.contentResolver.openInputStream(pending.uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Не удалось прочитать ${pending.name}")
            SharedFolderSyncResult(
                message = "Отчёт обновлён, найден новый план ${pending.name}",
                pendingPlanJson = raw,
                pendingPlanName = pending.name,
            )
        }
    }

    suspend fun createBackup(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) { "Общая папка недоступна" }
        require(root.canWrite()) { "Нет доступа на запись" }
        val folders = ensureLayout(root)
        val fileName = "mountain-form-backup-${LocalDate.now()}.json"
        writeText(folders.backups, fileName, repository.exportBackup())
        fileName
    }

    suspend fun archiveAppliedPlan(treeUri: Uri, name: String, rawJson: String) = withContext(Dispatchers.IO) {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) { "Общая папка недоступна" }
        val folders = ensureLayout(root)
        writeText(folders.applied, name, rawJson)
        folders.inbox.findFile(name)?.delete()
    }

    suspend fun readDocument(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Не удалось прочитать выбранный документ")
    }

    fun displayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private data class Layout(
        val reports: DocumentFile,
        val inbox: DocumentFile,
        val applied: DocumentFile,
        val backups: DocumentFile,
    )

    private fun ensureLayout(root: DocumentFile): Layout {
        val reports = root.directory("reports")
        val plans = root.directory("plans")
        return Layout(
            reports = reports,
            inbox = plans.directory("inbox"),
            applied = plans.directory("applied"),
            backups = root.directory("backups"),
        )
    }

    private fun DocumentFile.directory(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory }
            ?: requireNotNull(createDirectory(name)) { "Не удалось создать папку $name" }

    private fun writeText(directory: DocumentFile, name: String, content: String) {
        val tempName = "$name.tmp"
        directory.findFile(tempName)?.delete()
        val temp = requireNotNull(directory.createFile("application/json", tempName)) {
            "Не удалось создать временный файл $tempName"
        }
        context.contentResolver.openOutputStream(temp.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
            ?: error("Не удалось записать $tempName")
        directory.findFile(name)?.delete()
        if (!temp.renameTo(name)) {
            val target = requireNotNull(directory.createFile("application/json", name)) { "Не удалось создать $name" }
            context.contentResolver.openOutputStream(target.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
                ?: error("Не удалось записать $name")
            temp.delete()
        }
    }
}
