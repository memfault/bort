package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.memfault.bort.dropbox.DropBoxGetEntriesTask
import com.memfault.bort.dropbox.EntryProcessor
import com.memfault.bort.dropbox.RealDropBoxLastProcessedEntryProvider
import com.memfault.bort.dropbox.realDropBoxEntryProcessors
import com.memfault.bort.http.DebugInfoInjectingInterceptor
import com.memfault.bort.http.LoggingNetworkInterceptor
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.metrics.BatteryStatsHistoryCollector
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.metrics.RealLastHeartbeatEndTimeProvider
import com.memfault.bort.metrics.RealNextBatteryStatsHistoryStartProvider
import com.memfault.bort.metrics.runBatteryStats
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.time.RealBootRelativeTimeProvider
import com.memfault.bort.time.RealCombinedTimeProvider
import com.memfault.bort.uploader.EnqueueFileUpload
import com.memfault.bort.uploader.FileUploadTask
import com.memfault.bort.uploader.HttpTask
import com.memfault.bort.uploader.HttpTaskCallFactory
import com.memfault.bort.uploader.MemfaultFileUploader
import com.memfault.bort.uploader.PreparedUploadService
import com.memfault.bort.uploader.PreparedUploader
import com.memfault.bort.uploader.enqueueFileUploadTask
import java.io.File
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit

// A simple holder for application components
// This example uses a service locator pattern over a DI framework for simplicity and ease of use;
// if you would prefer to see a DI framework being used, please let us know!
data class AppComponents(
    val settingsProvider: SettingsProvider,
    val okHttpClient: OkHttpClient,
    val retrofitClient: Retrofit,
    val workerFactory: WorkerFactory,
    val fileUploaderFactory: FileUploaderFactory,
    val bortEnabledProvider: BortEnabledProvider,
    val deviceIdProvider: DeviceIdProvider,
    val deviceInfoProvider: DeviceInfoProvider,
    val httpTaskCallFactory: HttpTaskCallFactory,
    val ingressService: IngressService,
    val reporterServiceConnector: ReporterServiceConnector
) {
    open class Builder(
        private val context: Context,
        private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            context
        )
    ) {
        var settingsProvider: SettingsProvider? = null
        var loggingInterceptor: Interceptor? = null
        var debugInfoInjectingInterceptor: Interceptor? = null
        var okHttpClient: OkHttpClient? = null
        var retrofit: Retrofit? = null
        var interceptingWorkerFactory: InterceptingWorkerFactory? = null
        var fileUploaderFactory: FileUploaderFactory? = null
        var bortEnabledProvider: BortEnabledProvider? = null
        var deviceIdProvider: DeviceIdProvider? = null
        var reporterServiceConnector: ReporterServiceConnector? = null
        var extraDropBoxEntryProcessors: Map<String, EntryProcessor> = emptyMap()

        fun build(): AppComponents {
            val settingsProvider = settingsProvider ?: BuildConfigSettingsProvider()
            val deviceIdProvider = deviceIdProvider ?: RandomUuidDeviceIdProvider(sharedPreferences)
            val deviceInfoProvider = RealDeviceInfoProvider(settingsProvider.deviceInfoSettings)
            val fileUploaderFactory =
                fileUploaderFactory ?: MemfaultFileUploaderFactory()
            val projectKeyInjectingInterceptor = ProjectKeyInjectingInterceptor(
                settingsProvider.httpApiSettings::projectKey
            )
            val okHttpClient = okHttpClient ?: OkHttpClient.Builder()
                .addInterceptor(projectKeyInjectingInterceptor)
                .addInterceptor(
                    debugInfoInjectingInterceptor ?: DebugInfoInjectingInterceptor(
                        settingsProvider.sdkVersionInfo,
                        deviceIdProvider
                    )
                )
                .addInterceptor(loggingInterceptor ?: LoggingNetworkInterceptor())
                .build()
            val retrofit = retrofit ?: Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(settingsProvider.httpApiSettings.filesBaseUrl.toHttpUrl())
                .addConverterFactory(
                    kotlinxJsonConverterFactory()
                )
                .build()
            val bortEnabledProvider =
                bortEnabledProvider ?: if (settingsProvider.isRuntimeEnableRequired) {
                    PreferenceBortEnabledProvider(
                        sharedPreferences,
                        defaultValue = !settingsProvider.isRuntimeEnableRequired
                    )
                } else {
                    BortAlwaysEnabledProvider()
                }

            val reporterServiceConnector = reporterServiceConnector ?: RealReporterServiceConnector(
                context = context,
                inboundLooper = Looper.getMainLooper()
            )

            val temporaryFileFactory = object : TemporaryFileFactory {
                override val temporaryFileDirectory: File = context.cacheDir
            }
            val bootRelativeTimeProvider = RealBootRelativeTimeProvider(context)
            val packageManagerClient = PackageManagerClient(reporterServiceConnector)
            val enqueueFileUpload: EnqueueFileUpload = { file, metadata, debugTag ->
                enqueueFileUploadTask(
                    context, file, metadata, settingsProvider.httpApiSettings.uploadConstraints, debugTag
                )
            }
            val dropBoxEntryProcessors = realDropBoxEntryProcessors(
                tempFileFactory = temporaryFileFactory,
                bootRelativeTimeProvider = bootRelativeTimeProvider,
                enqueueFileUpload = enqueueFileUpload,
                packageManagerClient = packageManagerClient,
                deviceInfoProvider = deviceInfoProvider,
            ) + extraDropBoxEntryProcessors

            val workerFactory = DefaultWorkerFactory(
                context = context,
                settingsProvider = settingsProvider,
                bortEnabledProvider = bortEnabledProvider,
                retrofit = retrofit,
                fileUploaderFactory = fileUploaderFactory,
                okHttpClient = okHttpClient,
                reporterServiceConnector = reporterServiceConnector,
                dropBoxEntryProcessors = dropBoxEntryProcessors,
                enqueueFileUpload = enqueueFileUpload,
                temporaryFileFactory = temporaryFileFactory,
                interceptingFactory = interceptingWorkerFactory,
            )

            val httpTaskCallFactory = HttpTaskCallFactory.fromContextAndConstraints(
                context, settingsProvider.httpApiSettings.uploadConstraints, projectKeyInjectingInterceptor
            )

            return AppComponents(
                settingsProvider = settingsProvider,
                okHttpClient = okHttpClient,
                retrofitClient = retrofit,
                workerFactory = workerFactory,
                fileUploaderFactory = fileUploaderFactory,
                bortEnabledProvider = bortEnabledProvider,
                deviceIdProvider = deviceIdProvider,
                deviceInfoProvider = deviceInfoProvider,
                httpTaskCallFactory = httpTaskCallFactory,
                ingressService = IngressService.create(settingsProvider.httpApiSettings, httpTaskCallFactory),
                reporterServiceConnector = reporterServiceConnector
            )
        }
    }

    fun isEnabled(): Boolean = bortEnabledProvider.isEnabled()
}

@OptIn(ExperimentalSerializationApi::class)
fun kotlinxJsonConverterFactory(): Converter.Factory =
    BortJson.asConverterFactory("application/json".toMediaType())

interface DeviceIdProvider {
    fun deviceId(): String
}

class RandomUuidDeviceIdProvider(
    sharedPreferences: SharedPreferences
) : DeviceIdProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = UUID.randomUUID().toString(),
    preferenceKey = PREFERENCE_DEVICE_ID
) {
    override fun deviceId(): String = super.getValue()
}

interface InterceptingWorkerFactory {
    fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
        settingsProvider: SettingsProvider,
        reporterServiceConnector: ReporterServiceConnector
    ): ListenableWorker?
}

class DefaultWorkerFactory(
    private val context: Context,
    private val settingsProvider: SettingsProvider,
    private val bortEnabledProvider: BortEnabledProvider,
    private val retrofit: Retrofit,
    private val fileUploaderFactory: FileUploaderFactory,
    private val okHttpClient: OkHttpClient,
    private val reporterServiceConnector: ReporterServiceConnector,
    private val dropBoxEntryProcessors: Map<String, EntryProcessor>,
    private val enqueueFileUpload: EnqueueFileUpload,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val interceptingFactory: InterceptingWorkerFactory? = null
) : WorkerFactory(), TaskFactory {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        interceptingFactory?.createWorker(
            appContext,
            workerClassName,
            workerParameters,
            settingsProvider,
            reporterServiceConnector
        )?.let {
            return it
        }

        return when (workerClassName) {
            TaskRunnerWorker::class.qualifiedName ->
                TaskRunnerWorker(
                    appContext = appContext,
                    workerParameters = workerParameters,
                    taskFactory = this
                )
            else -> null
        }
    }

    override fun create(inputData: Data): Task<*>? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        return when (inputData.workDelegateClass) {
            HttpTask::class.qualifiedName -> HttpTask(okHttpClient = okHttpClient)
            FileUploadTask::class.qualifiedName -> FileUploadTask(
                delegate = fileUploaderFactory.create(retrofit, settingsProvider.httpApiSettings.projectKey),
                bortEnabledProvider = bortEnabledProvider,
                maxAttempts = settingsProvider.bugReportSettings.maxUploadAttempts
            )
            DropBoxGetEntriesTask::class.qualifiedName -> DropBoxGetEntriesTask(
                lastProcessedEntryProvider = RealDropBoxLastProcessedEntryProvider(sharedPreferences),
                reporterServiceConnector = reporterServiceConnector,
                entryProcessors = dropBoxEntryProcessors,
                settings = settingsProvider.dropBoxSettings,
            )
            MetricsCollectionTask::class.qualifiedName -> MetricsCollectionTask(
                batteryStatsHistoryCollector = BatteryStatsHistoryCollector(
                    temporaryFileFactory = temporaryFileFactory,
                    nextBatteryStatsHistoryStartProvider = RealNextBatteryStatsHistoryStartProvider(sharedPreferences),
                    runBatteryStats = reporterServiceConnector::runBatteryStats,
                ),
                enqueueFileUpload = enqueueFileUpload,
                combinedTimeProvider = RealCombinedTimeProvider(context),
                lastHeartbeatEndTimeProvider = RealLastHeartbeatEndTimeProvider(sharedPreferences),
                deviceInfoProvider = RealDeviceInfoProvider(settingsProvider.deviceInfoSettings),
            )
            else -> null
        }
    }
}

class MemfaultFileUploaderFactory : FileUploaderFactory {
    override fun create(retrofit: Retrofit, projectApiKey: String): FileUploader =
        MemfaultFileUploader(
            preparedUploader = PreparedUploader(
                preparedUploadService = retrofit.create(PreparedUploadService::class.java),
            )
        )
}

/**
 * A stub "enabled" provider; used only when the device does not require being enabled at runtime.
 */
class BortAlwaysEnabledProvider : BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) {
    }

    override fun isEnabled(): Boolean {
        return true
    }
}

/** A preference-backed provider of the user's opt in state. */
class PreferenceBortEnabledProvider(
    sharedPreferences: SharedPreferences,
    defaultValue: Boolean
) : PreferenceKeyProvider<Boolean>(
    sharedPreferences = sharedPreferences,
    defaultValue = defaultValue,
    preferenceKey = PREFERENCE_BORT_ENABLED
),
    BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) = setValue(isOptedIn)

    override fun isEnabled(): Boolean = getValue()
}
