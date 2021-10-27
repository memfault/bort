package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.memfault.bort.dropbox.testDropBoxEntryProcessors
import com.memfault.bort.http.logAttempt
import com.memfault.bort.http.logFailure
import com.memfault.bort.http.logTimeout
import com.memfault.bort.http.logTimings
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.requester.BugReportRequestWorker
import com.memfault.bort.selfTesting.SelfTestWorker
import com.memfault.bort.settings.DataScrubbingSettings
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RealStoredSettingsPreferenceProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.StoredSettingsPreferenceProvider
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority
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
            logTimeout {
                logAttempt()
                val request: Request = chain.request()
                val t1: Long = System.nanoTime()
                Logger.v("Sending request ${request.url} on ${chain.connection()} ${request.headers}")
                val response: Response = chain.proceed(request)
                val t2: Long = System.nanoTime()
                val delta = (t2 - t1) / 1e6
                logTimings(delta.toLong())
                Logger.v(
                    """Received response for ${response.request.url} in ${String.format("%.1f", delta)} ms
                   ${response.headers}
                    """.trimIndent()
                )
                if (!response.isSuccessful) {
                    logFailure(response.code)
                    Logger.w("Request failed! code=${response.code}, message=${response.message}")
                }

                response
            }
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
                bortSystemCapabilities: BortSystemCapabilities,
                builtInMetricsStore: BuiltinMetricsStore,
            ): ListenableWorker? = when (workerClassName) {
                BugReportRequestWorker::class.qualifiedName ->
                    object : BugReportRequestWorker(
                        appContext,
                        workerParameters,
                        pendingBugReportRequestAccessor,
                        bugReportPeriodicTaskTokenBucketStore,
                        settingsProvider.bugReportSettings,
                        bortSystemCapabilities = bortSystemCapabilities,
                        builtInMetricsStore = builtInMetricsStore,
                    ) {
                        override suspend fun captureBugReport(): Boolean {
                            Logger.i("** MFLT-TEST ** Periodic Bug Report Request")
                            return true
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

        // Don't apply jitter delay to http requests in tests
        jitterDelayProvider = JitterDelayProvider(applyJitter = false)
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
    }

    // TODO: review this, the backend will override settings through dynamic settings update
    //  and lower the log level from TEST to whatever is the default (usually verbose) and
    //  it makes some tests fail
    override val minLogcatLevel = LogLevel.TEST

    // Backend might return this as disabled but e2e tests require it
    override val dropBoxSettings = object : DropBoxSettings by super.dropBoxSettings {
        override val dataSourceEnabled = true
    }

    // Include bort-test tags for logcat collector
    override val logcatSettings = object : LogcatSettings by super.logcatSettings {
        override val filterSpecs: List<LogcatFilterSpec> =
            listOf(
                LogcatFilterSpec("*", LogcatPriority.WARN),
                LogcatFilterSpec("bort", LogcatPriority.VERBOSE),
                LogcatFilterSpec("bort-test", LogcatPriority.VERBOSE),
            )
    }

    // Include data scrubbing rules when testing
    override val dataScrubbingSettings = object : DataScrubbingSettings by super.dataScrubbingSettings {
        override val rules: List<DataScrubbingRule> = listOf(
            EmailScrubbingRule,
            CredentialScrubbingRule,
        )
    }
}
