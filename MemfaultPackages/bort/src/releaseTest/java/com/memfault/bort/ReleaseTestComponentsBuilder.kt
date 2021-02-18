package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.memfault.bort.dropbox.testDropBoxEntryProcessors
import com.memfault.bort.requester.BugReportRequestWorker
import com.memfault.bort.selfTesting.SelfTestWorker
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.RealStoredSettingsPreferenceProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.StoredSettingsPreferenceProvider
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class ReleaseTestComponentsBuilder(
    context: Context
) : AppComponents.Builder(
    context
) {
    init {
        loggingInterceptor = Interceptor { chain ->
            val request: Request = chain.request()
            val t1: Long = System.nanoTime()
            Logger.v("Sending request ${request.url} on ${chain.connection()} ${request.headers}")
            val response: Response = chain.proceed(request)
            val t2: Long = System.nanoTime()
            Logger.v(
                """Received response for ${response.request.url} in ${String.format("%.1f", (t2 - t1) / 1e6)} ms
                   ${response.headers}
                """.trimIndent()
            )
            if (!response.isSuccessful) {
                Logger.w("Request failed! code=${response.code}, message=${response.message}")
            }

            response
        }

        interceptingWorkerFactory = object : InterceptingWorkerFactory {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
                settingsProvider: SettingsProvider,
                reporterServiceConnector: ReporterServiceConnector,
                pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
                bugReportPeriodicTaskTokenBucketStore: TokenBucketStore,
            ): ListenableWorker? = when (workerClassName) {
                BugReportRequestWorker::class.qualifiedName ->
                    object : BugReportRequestWorker(
                        appContext,
                        workerParameters,
                        pendingBugReportRequestAccessor,
                        bugReportPeriodicTaskTokenBucketStore,
                    ) {
                        override fun doWork(): Result {
                            Logger.i("** MFLT-TEST ** Periodic Bug Report Request")
                            return Result.success()
                        }
                    }
                SelfTestWorker::class.qualifiedName -> SelfTestWorker(
                    appContext = appContext,
                    workerParameters = workerParameters,
                    reporterServiceConnector = reporterServiceConnector,
                    settingsProvider = settingsProvider
                )
                else -> null
            }
        }

        settingsProvider = PersistentSettingsProvider(
            storedSettingsPreferenceProvider = RealStoredSettingsPreferenceProvider(
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context),
                getBundledConfig = {
                    context.resources.assets
                        .open(DEFAULT_SETTINGS_ASSET_FILENAME)
                        .use {
                            it.bufferedReader().readText()
                        }
                }
            ),
            PersistentProjectKeyProvider(
                PreferenceManager.getDefaultSharedPreferences(context)
            ),
        )

        extraDropBoxEntryProcessors = testDropBoxEntryProcessors()
    }
}

class PersistentProjectKeyProvider(
    sharedPreferences: SharedPreferences
) : PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = BuildConfig.MEMFAULT_PROJECT_API_KEY,
    preferenceKey = "test-project-api-key"
)

class PersistentSettingsProvider(
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    private val persistentProjectKeyProvider: PersistentProjectKeyProvider,
) : DynamicSettingsProvider(storedSettingsPreferenceProvider) {
    override val httpApiSettings = object : HttpApiSettings by super.httpApiSettings {
        override val projectKey: String
            get() = persistentProjectKeyProvider.getValue()

        // TODO: ideally these would not be overloaded but the backend is currently returning
        //  production urls in settings update payloads, even in test environments. When the
        //  settings update job is triggered, the tests would start pointing at production
        //  urls. The workaround for now is to use those specified in BuildConfig
        override val deviceBaseUrl: String = BuildConfig.MEMFAULT_DEVICE_BASE_URL
        override val filesBaseUrl: String = BuildConfig.MEMFAULT_FILES_BASE_URL
        override val ingressBaseUrl: String = BuildConfig.MEMFAULT_INGRESS_BASE_URL
    }

    // TODO: review this, the backend will override settings through dynamic settings update
    //  and lower the log level from TEST to whatever is the default (usually verbose) and
    //  it makes some tests fail
    override val minLogLevel = LogLevel.TEST

    // Backend might return this as disabled but e2e tests require it
    override val dropBoxSettings = object : DropBoxSettings by super.dropBoxSettings {
        override val dataSourceEnabled = true
    }
}
