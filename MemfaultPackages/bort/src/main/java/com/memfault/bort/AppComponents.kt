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
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.uploader.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import java.util.*

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
        var dropBoxEntryProcessors: Map<String, EntryProcessor>? = null

        fun build(): AppComponents {
            val settingsProvider = settingsProvider ?: BuildConfigSettingsProvider()
            val deviceIdProvider = deviceIdProvider ?: RandomUuidDeviceIdProvider(sharedPreferences)
            val fileUploaderFactory = fileUploaderFactory ?: MemfaultFileUploaderFactory()
            val projectKeyInjectingInterceptor = ProjectKeyInjectingInterceptor(settingsProvider::projectKey)
            val okHttpClient = okHttpClient ?: OkHttpClient.Builder()
                .addInterceptor(projectKeyInjectingInterceptor)
                .addInterceptor(
                    debugInfoInjectingInterceptor ?: DebugInfoInjectingInterceptor(
                        settingsProvider,
                        deviceIdProvider
                    )
                )
                .addInterceptor(loggingInterceptor ?: LoggingNetworkInterceptor())
                .build()
            val retrofit = retrofit ?: Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(HttpUrl.get(settingsProvider.filesBaseUrl()))
                .addConverterFactory(
                    kotlinxJsonConverterFactory()
                )
                .build()
            val bortEnabledProvider =
                bortEnabledProvider ?: if (settingsProvider.isRuntimeEnableRequired()) {
                    PreferenceBortEnabledProvider(
                        sharedPreferences,
                        defaultValue = !settingsProvider.isRuntimeEnableRequired()
                    )
                } else {
                    BortAlwaysEnabledProvider()
                }

            val reporterServiceConnector = reporterServiceConnector ?: RealReporterServiceConnector(
                context = context,
                inboundLooper = Looper.getMainLooper()
            )
            val dropBoxEntryProcessors = dropBoxEntryProcessors ?: realDropBoxEntryProcessors()

            val workerFactory = DefaultWorkerFactory(
                context = context,
                settingsProvider = settingsProvider,
                bortEnabledProvider = bortEnabledProvider,
                retrofit = retrofit,
                fileUploaderFactory = fileUploaderFactory,
                okHttpClient = okHttpClient,
                deviceIdProvider = deviceIdProvider,
                reporterServiceConnector = reporterServiceConnector,
                dropBoxEntryProcessors = dropBoxEntryProcessors,
                interceptingFactory = interceptingWorkerFactory
            )

            val httpTaskCallFactory = HttpTaskCallFactory.fromContextAndConstraints(
                context, settingsProvider.uploadConstraints(), projectKeyInjectingInterceptor
            )

            return AppComponents(
                settingsProvider = settingsProvider,
                okHttpClient = okHttpClient,
                retrofitClient = retrofit,
                workerFactory = workerFactory,
                fileUploaderFactory = fileUploaderFactory,
                bortEnabledProvider = bortEnabledProvider,
                deviceIdProvider = deviceIdProvider,
                httpTaskCallFactory = httpTaskCallFactory,
                ingressService = IngressService.create(settingsProvider, httpTaskCallFactory),
                reporterServiceConnector = reporterServiceConnector
            )
        }
    }

    fun isEnabled(): Boolean = bortEnabledProvider.isEnabled()
}

fun kotlinxJsonConverterFactory(): Converter.Factory =
    Json(JsonConfiguration.Stable)
        .asConverterFactory(MediaType.get("application/json"))

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

open class BuildConfigSettingsProvider : SettingsProvider {
    override fun bugReportRequestIntervalHours() =
        BuildConfig.BUG_REPORT_REQUEST_INTERVAL_HOURS.toLong()

    override fun firstBugReportDelayAfterBootMinutes(): Long =
        BuildConfig.FIRST_BUG_REPORT_DELAY_AFTER_BOOT_MINUTES.toLong()

    override fun minLogLevel(): LogLevel =
        LogLevel.fromInt(BuildConfig.MINIMUM_LOG_LEVEL) ?: LogLevel.VERBOSE

    override fun bugReportNetworkConstraint(): NetworkConstraint =
        if (BuildConfig.UPLOAD_NETWORK_CONSTRAINT_ALLOW_METERED_CONNECTION) NetworkConstraint.CONNECTED
        else NetworkConstraint.UNMETERED

    override fun maxUploadAttempts(): Int = BuildConfig.BUG_REPORT_MAX_UPLOAD_ATTEMPTS

    override fun isRuntimeEnableRequired(): Boolean = BuildConfig.RUNTIME_ENABLE_REQUIRED

    override fun projectKey(): String = BuildConfig.MEMFAULT_PROJECT_API_KEY

    override fun filesBaseUrl(): String = BuildConfig.MEMFAULT_FILES_BASE_URL

    override fun ingressBaseUrl(): String = BuildConfig.MEMFAULT_INGRESS_BASE_URL

    override fun appVersionName(): String = BuildConfig.VERSION_NAME

    override fun appVersionCode(): Int = BuildConfig.VERSION_CODE

    override fun upstreamVersionName(): String = BuildConfig.UPSTREAM_VERSION_NAME

    override fun upstreamVersionCode(): Int = BuildConfig.UPSTREAM_VERSION_CODE

    override fun upstreamGitSha(): String = BuildConfig.UPSTREAM_GIT_SHA

    override fun currentGitSha(): String = BuildConfig.CURRENT_GIT_SHA

    override fun androidBuildVersionKey(): String = BuildConfig.ANDROID_BUILD_VERSION_KEY

    override fun androidHardwareVersionKey(): String = BuildConfig.ANDROID_HARDWARE_VERSION_KEY

    override fun androidSerialNumberKey(): String = BuildConfig.ANDROID_DEVICE_SERIAL_KEY
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
    private val deviceIdProvider: DeviceIdProvider,
    private val reporterServiceConnector: ReporterServiceConnector,
    private val dropBoxEntryProcessors: Map<String, EntryProcessor>,
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

        return when (inputData.workDelegateClass) {
            HttpTask::class.qualifiedName -> HttpTask(okHttpClient = okHttpClient)
            BugReportUploader::class.qualifiedName -> BugReportUploader(
                delegate = fileUploaderFactory.create(retrofit, settingsProvider.projectKey()),
                bortEnabledProvider = bortEnabledProvider,
                maxAttempts = settingsProvider.maxUploadAttempts()
            )
            DropBoxGetEntriesTask::class.qualifiedName -> DropBoxGetEntriesTask(
                lastProcessedEntryProvider = RealDropBoxLastProcessedEntryProvider(
                    PreferenceManager.getDefaultSharedPreferences(context)
                ),
                reporterServiceConnector = reporterServiceConnector,
                entryProcessors = dropBoxEntryProcessors
            )
            else -> null
        }
    }
}

class MemfaultFileUploaderFactory : FileUploaderFactory {
    override fun create(retrofit: Retrofit, projectApiKey: String): FileUploader =
        MemfaultBugReportUploader(
            preparedUploader = PreparedUploader(
                retrofit.create(PreparedUploadService::class.java)
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
), BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) = setValue(isOptedIn)

    override fun isEnabled(): Boolean = getValue()
}
