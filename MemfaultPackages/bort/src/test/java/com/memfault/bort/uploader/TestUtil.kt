package com.memfault.bort.uploader

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.Task
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.addWorkDelegateClass
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.kotlinxJsonConverterFactory
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.SettingsUpdateTask
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import java.io.File

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

inline fun <reified K : Task<*>> mockTaskRunnerWorker(
    context: Context,
    workerFactory: WorkerFactory,
    inputData: Data,
    runAttemptCount: Int = 1,
): TaskRunnerWorker = TestListenableWorkerBuilder<TaskRunnerWorker>(
    context = context,
    inputData = addWorkDelegateClass(
        checkNotNull(K::class.qualifiedName),
        inputData,
    ),
    runAttemptCount = runAttemptCount,
).apply { setWorkerFactory(workerFactory) }
    .build()

private class TestTaskRunnerWorker(
    appContext: Context,
    params: WorkerParameters,
    private val overrideTask: Task<*>?,
    settingsUpdate: SettingsUpdateTask? = null,
    fileUpload: FileUploadTask? = null,
) : TaskRunnerWorker(
    appContext = appContext,
    params = params,
    fileUpload = { fileUpload },
    dropBox = { error("Unimplemented") },
    metrics = { error("Unimplemented") },
    bugReportTimeout = { error("Unimplemented") },
    logcat = { error("Unimplemented") },
    settings = { settingsUpdate },
    marBatching = { error("Unimplemented") },
    bortJobReporter = mockk(relaxed = true),
    builtInMetricsStore = BuiltinMetricsStore(),
) {
    override fun createTask(inputData: Data): Task<*>? =
        overrideTask ?: super.createTask(inputData)
}

fun mockWorkerFactory(
    overrideTask: Task<*>? = null,
    settingsUpdate: SettingsUpdateTask? = null,
    fileUpload: FileUploadTask? = null,
): WorkerFactory = object : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker = TestTaskRunnerWorker(
        appContext = appContext,
        params = workerParameters,
        overrideTask = overrideTask,
        settingsUpdate = settingsUpdate,
        fileUpload = fileUpload,
    )
}

class BortEnabledTestProvider(
    private var enabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
) : BortEnabledProvider {

    override fun setEnabled(isOptedIn: Boolean) {
        enabled.value = isOptedIn
    }

    override fun isEnabled(): Boolean = enabled.value
    override fun isEnabledFlow(): Flow<Boolean> = enabled

    override fun requiresRuntimeEnable(): Boolean = true
}
