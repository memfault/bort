package com.memfault.bort.uploader

import androidx.work.Data
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.kotlinxJsonConverterFactory
import com.memfault.bort.settings.BortEnabledProvider
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import java.io.File
import java.util.UUID
import kotlin.String

const val UPLOAD_URL = "https://test.com/abc"
const val AUTH_TOKEN = "auth_token"
const val UPLOAD_RESPONSE =
    """
        {
            "data": {
                "upload_url": "$UPLOAD_URL",
                "token": "$AUTH_TOKEN"
            }
        }
    """

const val SECRET_KEY = "secretKey"

internal fun createUploader(server: MockWebServer) =
    PreparedUploader(
        createRetrofit(server).create(PreparedUploadService::class.java),
        eventLogger = object : UploadEventLogger {
            override fun log(vararg strings: String) {}
        },
        deviceInfoProvider = FakeDeviceInfoProvider(),
    )

fun createRetrofit(server: MockWebServer) =
    Retrofit.Builder()
        .client(
            OkHttpClient.Builder()
                .addInterceptor(ProjectKeyInjectingInterceptor({ SECRET_KEY }))
                .build(),
        )
        .baseUrl(server.url("/"))
        .addConverterFactory(kotlinxJsonConverterFactory())
        .build()

fun loadTestFileFromResources() = File(
    PreparedUploaderTest::class.java.getResource("/test.txt")!!.path,
)

fun mockTaskRunnerWorker(inputData: Data, runAttemptCount: Int = 1) =
    mockk<TaskRunnerWorker> {
        every { getRunAttemptCount() } returns runAttemptCount
        every { getInputData() } returns inputData
        every { id } returns UUID.randomUUID()
        every { tags } returns emptySet()
    }

class BortEnabledTestProvider(private var enabled: Boolean = true) : BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) {
        enabled = isOptedIn
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun requiresRuntimeEnable(): Boolean = true
}
