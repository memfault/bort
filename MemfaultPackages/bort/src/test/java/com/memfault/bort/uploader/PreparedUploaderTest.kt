package com.memfault.bort.uploader

import com.memfault.bort.http.PROJECT_KEY_HEADER
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PreparedUploaderTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun prepareProvidesUrlAndToken() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        val result = runBlocking {
            createUploader(server).prepare()
        }
        assertEquals(SECRET_KEY, server.takeRequest().getHeader(PROJECT_KEY_HEADER))
        assertEquals(UPLOAD_URL, result.body()!!.data.upload_url)
        assertEquals(AUTH_TOKEN, result.body()!!.data.token)
    }

    @Test
    fun uploadFile() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        runBlocking {
            createUploader(server).upload(
                loadTestFileFromResources(),
                // Force the request to go to the mock server so that we can inspect it
                server.url("test").toString()
            )
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        assertNotNull(recordedRequest)
        // Project key should not be included
        assertNull(recordedRequest!!.getHeader(PROJECT_KEY_HEADER))
        assertEquals("PUT", recordedRequest.method)
        assertEquals("application/octet-stream", recordedRequest.getHeader("Content-Type"))
        val text = loadTestFileFromResources().readText(Charset.defaultCharset())
        assertEquals(text, recordedRequest.body.readUtf8())
    }

    @Test
    fun uploadRequestToExternalServer() {
        server.enqueue(
            MockResponse()
                .setBody(UPLOAD_RESPONSE)
        )
        runBlocking {
            createUploader(server).upload(
                loadTestFileFromResources(),
                UPLOAD_URL
            )
        }
        // Verify that the the request is routed to an external server, not the `baseUrl`
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        assertNull(recordedRequest)
    }

    @Test
    fun commit() {
        server.enqueue(MockResponse())
        runBlocking {
            createUploader(server).commitBugreport("someToken")
        }
        val recordedRequest = server.takeRequest(5, TimeUnit.MILLISECONDS)
        checkNotNull(recordedRequest)
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"))
        assertEquals(SECRET_KEY, recordedRequest.getHeader(PROJECT_KEY_HEADER))
        assertEquals("""{"file":{"token":"someToken"}}""", recordedRequest.body.readUtf8())
    }
}
