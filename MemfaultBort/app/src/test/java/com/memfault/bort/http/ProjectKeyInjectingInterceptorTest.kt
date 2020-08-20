package com.memfault.bort.http

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface AuthTestService {
    @POST("/authenticatedApi")
    @ProjectKeyAuthenticated
    suspend fun authenticatedApi(
    ): Response<Unit>

    @POST("/openApi")
    suspend fun openApi(
    ): Response<Unit>
}

private const val SECRET = "SECRET"

class ProjectKeyInjectingInterceptorTest {
    @get:Rule
    val server = MockWebServer()

    lateinit var service: AuthTestService

    @Before
    fun before() {
        service = Retrofit.Builder()
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(ProjectKeyInjectingInterceptor({ SECRET }))
                    .build()
            )
            .baseUrl(server.url("/"))
            .build()
            .create(AuthTestService::class.java)
    }

    @Test
    fun authedApi() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200))
            service.authenticatedApi()
            server.takeRequest(5, TimeUnit.MILLISECONDS).let {
                assertNotNull(it)
                assertEquals(SECRET, it.getHeader(PROJECT_KEY_HEADER))
            }
        }
    }

    @Test
    fun openApi() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200))
            service.openApi()
            server.takeRequest(5, TimeUnit.MILLISECONDS).let {
                assertNotNull(it)
                assertNull(it.getHeader(PROJECT_KEY_HEADER))
            }
        }
    }
}
