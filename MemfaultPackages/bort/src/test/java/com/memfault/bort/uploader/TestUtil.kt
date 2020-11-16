package com.memfault.bort.uploader

import androidx.work.Data
import com.memfault.bort.BortEnabledProvider
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.kotlinxJsonConverterFactory
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.io.File
import kotlin.String
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

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
        object : DeviceInfoProvider {
            override suspend fun getDeviceInfo(): DeviceInfo =
                DeviceInfo("SN1234", "HW-FOO", "1.0.0")
        },
        eventLogger = object : UploadEventLogger {
            override fun log(vararg strings: String) {}
        }
    )

fun createRetrofit(server: MockWebServer) =
    Retrofit.Builder()
        .client(
            OkHttpClient.Builder()
                .addInterceptor(ProjectKeyInjectingInterceptor({ SECRET_KEY }))
                .build()
        )
        .baseUrl(server.url("/"))
        .addConverterFactory(kotlinxJsonConverterFactory())
        .build()

fun loadTestFileFromResources() = File(
    PreparedUploaderTest::class.java.getResource("/test.txt")!!.path
)

fun mockTaskRunnerWorker(inputData: Data, runAttemptCount: Int = 1) =
    mock<TaskRunnerWorker> {
        on { getRunAttemptCount() } doReturn runAttemptCount
        on { getInputData() } doReturn inputData
    }

class BortEnabledTestProvider(private var enabled: Boolean = true) : BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) {
        enabled = isOptedIn
    }

    override fun isEnabled(): Boolean {
        return enabled
    }
}
