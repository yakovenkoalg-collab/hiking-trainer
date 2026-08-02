package ru.yakovenko.mountainform.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class YandexDiskResponseTest {
    @Test
    fun existingDirectoryResponseReadsErrorBodyWithoutOpeningInputStream() {
        val connection = FakeConnection(
            statusCode = 409,
            errorBody = "already exists",
        )

        val body = yandexResponseStream(connection, connection.responseCode)
            ?.bufferedReader()
            ?.use { it.readText() }

        assertEquals("already exists", body)
        assertEquals(0, connection.inputStreamReads)
    }

    private class FakeConnection(
        private val statusCode: Int,
        errorBody: String,
    ) : HttpURLConnection(URL("https://example.invalid")) {
        private val errorBodyStream = ByteArrayInputStream(errorBody.toByteArray())
        var inputStreamReads: Int = 0

        override fun getResponseCode(): Int = statusCode

        override fun getInputStream(): InputStream {
            inputStreamReads += 1
            error("Input stream must not be opened for an HTTP error response")
        }

        override fun getErrorStream(): InputStream = errorBodyStream

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit
    }
}
