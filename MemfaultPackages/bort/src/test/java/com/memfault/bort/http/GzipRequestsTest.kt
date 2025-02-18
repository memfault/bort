package com.memfault.bort.http

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
        assertThat(body.contentLength()).isNotEqualTo(-1)
        body.writeTo(buffer)
        assertThat(GzipSource(buffer).buffer().readUtf8()).isEqualTo("hello")
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

    @Before
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
            assertThat(it).isNotNull()
            it as RecordedRequest

            assertThat(it.getHeader("Content-Encoding")).isEqualTo("gzip")
            assertThat(GzipSource(it.body).buffer().readUtf8()).isEqualTo("hello")
        }
    }

    @Test
    fun plainApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        service.plainApi("hello".toRequestBody())
        server.takeRequest(5, TimeUnit.MILLISECONDS).let {
            assertThat(it).isNotNull()
            it as RecordedRequest

            assertThat(it.getHeader("Content-Encoding")).isNull()
            assertThat(it.body.readUtf8()).isEqualTo("hello")
        }
    }
}
