package com.memfault.bort

import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.uploader.HttpTask
import com.memfault.bort.uploader.HttpTaskCallFactory
import com.memfault.bort.uploader.HttpTaskInput
import com.memfault.bort.uploader.HttpTaskOptions
import com.memfault.bort.uploader.mockTaskRunnerWorker
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Tag

private const val HEADER_KEY = "header_key"

@Serializable
data class HttpTaskTestApiBodyData(
    val data: String
)

@Serializable
data class HttpTaskTestApiResult(
    val data: String
)

interface HttpTaskTestService {
    @POST("/api/{sub_path}")
    @ProjectKeyAuthenticated
    fun testApi(
        @Header(HEADER_KEY) headerValue: String,
        @Path(value = "sub_path") subPath: String,
        @Body body: HttpTaskTestApiBodyData,
        @Tag options: HttpTaskOptions
    ): Call<HttpTaskTestApiResult>

    @POST("/api/suspend")
    suspend fun testApiSuspend(): Response<Unit>
}

class HttpTaskCallFactoryTest {
    @get:Rule
    val server = MockWebServer()

    lateinit var service: HttpTaskTestService
    var input: HttpTaskInput? = null

    @BeforeEach
    fun before() {
        val projectKeyInjector = ProjectKeyInjectingInterceptor({ "SECRET" })
        val client = OkHttpClient.Builder()
            .addInterceptor(projectKeyInjector).build()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(kotlinxJsonConverterFactory())
            .callFactory(HttpTaskCallFactory({ input = it }, projectKeyInjector))
            .build()
            .create(HttpTaskTestService::class.java)
    }

    @Test
    fun callsEnqueueHttpCallTask() {
        val response = service.testApi(
            headerValue = "foo",
            subPath = "bar",
            body = HttpTaskTestApiBodyData("lo"),
            options = HttpTaskOptions(
                maxAttempts = 123,
                taskTags = listOf("myTag")
            )
        ).execute()

        assertNotNull(input)
        input?.let {
            assertEquals(server.url("/api/bar").toString(), it.url)
            assertEquals("POST", it.method)
            assertEquals("header_key: foo\nMemfault-Project-Key: SECRET\n", it.headers)
            assertEquals("application/json; charset=utf-8", it.bodyMediaType)
            assertEquals("""{"data":"lo"}""", it.body.toString(Charsets.UTF_8))
            assertEquals(123, it.maxAttempts)
            assertEquals(listOf("myTag"), it.taskTags)
        }

        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())
        assertNull(response.body())
        assertEquals(Headers.Builder().build(), response.headers())
    }

    @Test
    fun callsEnqueueHttpCallTaskForSuspendFunction() {
        val response = runBlocking {
            service.testApiSuspend()
        }
        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())
    }
}

class HttpTaskTestMaxAttempts {
    @Test
    fun getMaxAttemptsFromInput() {
        val mockClient = mockk<OkHttpClient>(relaxed = true)
        val task = HttpTask(mockClient)
        val httpTaskInput = HttpTaskInput(
            "", "", "", "", ByteArray(0), maxAttempts = 123
        )
        assertEquals(123, task.getMaxAttempts(httpTaskInput))
    }
}
class HttpTaskTest {
    @get:Rule
    val server = MockWebServer()

    lateinit var client: OkHttpClient
    lateinit var task: HttpTask
    lateinit var httpTaskInput: HttpTaskInput
    lateinit var worker: TaskRunnerWorker

    @BeforeEach
    fun before() {
        client = OkHttpClient()
        task = HttpTask(client)
        httpTaskInput = HttpTaskInput(
            url = server.url("/test").toString(),
            method = "POST",
            bodyMediaType = "application/json; charset=utf-8",
            headers = "",
            body = ByteArray(0),
            taskTags = listOf("foo", "bar")
        )
        worker = mockTaskRunnerWorker(httpTaskInput.toWorkerInputData())
    }

    @Test
    fun httpOk() {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = runBlocking {
            task.doWork(worker)
        }
        assertEquals(TaskResult.SUCCESS, result)
        // TODO: assert Logger.logEvent calls
    }

    @Test
    fun httpError() {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = runBlocking {
            task.doWork(worker)
        }
        assertEquals(TaskResult.RETRY, result)
        // TODO: assert Logger.logEvent calls
    }
}
