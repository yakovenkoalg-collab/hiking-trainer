package ru.yakovenko.mountainform.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.yakovenko.mountainform.data.MountainFormRepository
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate

class YandexDiskSyncManager(
    private val repository: MountainFormRepository,
    private val tokenStore: SecureTokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun connect(token: String, rootPath: String): String = withContext(Dispatchers.IO) {
        tokenStore.save(token)
        runCatching {
            ensureLayout(rootPath, token)
            accountLabel(token)
        }.getOrElse {
            tokenStore.clear()
            throw it
        }
    }

    suspend fun sync(rootPath: String): SharedFolderSyncResult = withContext(Dispatchers.IO) {
        val token = requireToken()
        val root = normalizedRoot(rootPath)
        ensureLayout(root, token)
        upload("$root/reports/current-report.json", repository.exportReport(), token)
        val pending = list("$root/plans/inbox", token)
            .filter { it.name.endsWith(".json", ignoreCase = true) }
            .maxByOrNull { it.modified }
        if (pending == null) {
            SharedFolderSyncResult("Отчёт отправлен на Яндекс Диск, новых планов нет")
        } else {
            SharedFolderSyncResult(
                message = "Отчёт отправлен, найден новый план ${pending.name}",
                pendingPlanJson = download(pending.path, token),
                pendingPlanName = pending.name,
            )
        }
    }

    suspend fun createBackup(rootPath: String): String = withContext(Dispatchers.IO) {
        val token = requireToken()
        val root = normalizedRoot(rootPath)
        ensureLayout(root, token)
        val name = "mountain-form-backup-${LocalDate.now()}.json"
        upload("$root/backups/$name", repository.exportBackup(), token)
        name
    }

    suspend fun archiveAppliedPlan(rootPath: String, name: String, rawJson: String) = withContext(Dispatchers.IO) {
        val token = requireToken()
        val root = normalizedRoot(rootPath)
        upload("$root/plans/applied/$name", rawJson, token)
        request(
            "DELETE",
            "/resources",
            token,
            mapOf("path" to "$root/plans/inbox/$name"),
            allowedCodes = setOf(202, 204),
            allowNotFound = true,
        )
        Unit
    }

    fun disconnect() = tokenStore.clear()

    fun isConnected(): Boolean = tokenStore.hasToken()

    private fun ensureLayout(rootPath: String, token: String) {
        val root = normalizedRoot(rootPath)
        val scheme = if (root.startsWith("app:")) "app:" else "disk:"
        val segments = root.removePrefix("$scheme/").removePrefix(scheme).split('/').filter { it.isNotBlank() }
        var current = scheme
        segments.forEach { segment ->
            current += "/$segment"
            createDirectory(current, token)
        }
        listOf("reports", "plans", "plans/inbox", "plans/applied", "backups").forEach {
            var path = root
            it.split('/').forEach { part ->
                path += "/$part"
                createDirectory(path, token)
            }
        }
    }

    private fun createDirectory(path: String, token: String) {
        request("PUT", "/resources", token, mapOf("path" to path), allowedCodes = setOf(201, 409))
    }

    private fun upload(path: String, content: String, token: String) {
        val link = request(
            "GET",
            "/resources/upload",
            token,
            mapOf("path" to path, "overwrite" to "true"),
        )
        val href = json.parseToJsonElement(link).jsonObject.getValue("href").jsonPrimitive.content
        absoluteRequest("PUT", href, null, content.toByteArray(Charsets.UTF_8), setOf(201, 202))
    }

    private fun download(path: String, token: String): String {
        val link = request("GET", "/resources/download", token, mapOf("path" to path))
        val href = json.parseToJsonElement(link).jsonObject.getValue("href").jsonPrimitive.content
        return absoluteRequest("GET", href, null, null, setOf(200))
    }

    private fun list(path: String, token: String): List<DiskItem> {
        val body = request("GET", "/resources", token, mapOf("path" to path, "limit" to "100"))
        val items = json.parseToJsonElement(body).jsonObject["_embedded"]
            ?.jsonObject?.get("items")?.jsonArray.orEmpty()
        return items.mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val itemPath = item["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            DiskItem(name, itemPath, item["modified"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }

    private fun request(
        method: String,
        endpoint: String,
        token: String,
        query: Map<String, String> = emptyMap(),
        allowedCodes: Set<Int> = setOf(200),
        allowNotFound: Boolean = false,
    ): String {
        val suffix = query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val url = "$API$endpoint${if (suffix.isBlank()) "" else "?$suffix"}"
        return absoluteRequest(
            method = method,
            url = url,
            token = token,
            body = null,
            allowedCodes = allowedCodes + if (allowNotFound) setOf(404) else emptySet(),
        )
    }

    private fun absoluteRequest(
        method: String,
        url: String,
        token: String?,
        body: ByteArray?,
        allowedCodes: Set<Int>,
    ): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "OAuth $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = yandexResponseStream(connection, code)
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in allowedCodes) {
                val description = runCatching {
                    json.parseToJsonElement(response).jsonObject["description"]?.jsonPrimitive?.content
                }.getOrNull()
                error(description ?: "Яндекс Диск: HTTP $code")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun requireToken(): String = tokenStore.load() ?: error("Сначала подключите Яндекс Диск")

    private fun accountLabel(token: String): String = runCatching {
        val response = absoluteRequest("GET", ACCOUNT_INFO_API, token, null, setOf(200))
        val account = json.parseToJsonElement(response).jsonObject
        account["display_name"]?.jsonPrimitive?.contentOrNull
            ?: account["login"]?.jsonPrimitive?.contentOrNull
            ?: "Яндекс ID"
    }.getOrDefault("Яндекс ID")

    private fun normalizedRoot(path: String): String = path.trim().trimEnd('/').let {
        when {
            it.isBlank() -> "app:"
            it == "app:" || it.startsWith("app:/") -> it
            it == "disk:" || it.startsWith("disk:/") -> it
            else -> "disk:/${it.trimStart('/')}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class DiskItem(val name: String, val path: String, val modified: String)

    private companion object {
        const val API = "https://cloud-api.yandex.net/v1/disk"
        const val ACCOUNT_INFO_API = "https://login.yandex.ru/info?format=json"
    }
}

internal fun yandexResponseStream(connection: HttpURLConnection, responseCode: Int) =
    if (responseCode >= 400) connection.errorStream else connection.inputStream
