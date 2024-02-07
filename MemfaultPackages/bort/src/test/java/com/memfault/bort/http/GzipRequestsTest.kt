package com.memfault.bort.http

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.GzipSource
import okio.buffer
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

class GzipRequestBodyTest {
    @Test
    fun gzipRequestBody() {
        val body = "hello".toRequestBody().gzip()
        val buffer = Buffer()
        assertNotEquals(-1, body.contentLength())
        body.writeTo(buffer)
        assertEquals("hello", GzipSource(buffer).buffer().readUtf8())
    }
}

private interface TestService {
    @POST("/gzip")
    @GzipRequest
    suspend fun gzipSupportedApi(
        @Body body: RequestBody,
    ): Response<Unit>

    @POST("/plain")
    suspend fun plainApi(
        @Body body: RequestBody,
    ): Response<Unit>
}

class GzipInterceptorTest {
    @get:Rule
    val server = MockWebServer()
    private lateinit var service: TestService

    @BeforeEach
    fun before() {
        service = Retrofit.Builder()
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(GzipRequestInterceptor())
                    .build(),
            )
            .baseUrl(server.url("/"))
            .build()
            .create(TestService::class.java)
    }

    @Test
    fun gzipSupportedApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        service.gzipSupportedApi("hello".toRequestBody())
        server.takeRequest(5, TimeUnit.MILLISECONDS).let {
            assertNotNull(it)
            it as RecordedRequest

            assertEquals("gzip", it.getHeader("Content-Encoding"))
            assertEquals("hello", GzipSource(it.body).buffer().readUtf8())
        }
    }

    @Test
    fun plainApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        service.plainApi("hello".toRequestBody())
        server.takeRequest(5, TimeUnit.MILLISECONDS).let {
            assertNotNull(it)
            it as RecordedRequest

            assertEquals(null, it.getHeader("Content-Encoding"))
            assertEquals("hello", it.body.readUtf8())
        }
    }
}
