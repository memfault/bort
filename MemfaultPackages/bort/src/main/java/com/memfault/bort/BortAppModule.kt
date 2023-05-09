package com.memfault.bort

import android.app.Application
import android.content.Context
import android.content.res.Resources
import com.memfault.bort.logcat.KernelOopsDetector
import com.memfault.bort.logcat.NoopLogcatLineProcessor
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.BundledConfig
import com.memfault.bort.settings.DeviceConfigUpdateService
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.SettingsUpdateService
import com.memfault.bort.settings.readBundledSettings
import com.memfault.bort.shared.BASIC_COMMAND_TIMEOUT_MS
import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter.APPLY
import com.memfault.bort.tokenbucket.Anr
import com.memfault.bort.tokenbucket.BugReportPeriodic
import com.memfault.bort.tokenbucket.BugReportRequestStore
import com.memfault.bort.tokenbucket.ContinuousLogFile
import com.memfault.bort.tokenbucket.HighResMetricsFile
import com.memfault.bort.tokenbucket.JavaException
import com.memfault.bort.tokenbucket.KernelOops
import com.memfault.bort.tokenbucket.Kmsg
import com.memfault.bort.tokenbucket.Logcat
import com.memfault.bort.tokenbucket.MarDropbox
import com.memfault.bort.tokenbucket.MetricReportStore
import com.memfault.bort.tokenbucket.MetricsCollection
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import com.memfault.bort.tokenbucket.RealTokenBucketStorage.Companion.createFor
import com.memfault.bort.tokenbucket.RealTokenBucketStore
import com.memfault.bort.tokenbucket.Reboots
import com.memfault.bort.tokenbucket.StructuredLog
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.Tombstone
import com.memfault.bort.tokenbucket.Wtf
import com.memfault.bort.tokenbucket.WtfTotal
import com.memfault.bort.uploader.PreparedUploadService
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.time.toJavaDuration
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import vnd.myandroid.bortappid.CustomLogScrubber

/**
 * Bindings which are applicable only to the "release" build. These are replaced by DebugModule in the
 * debug build
 */
@Module
@ContributesTo(SingletonComponent::class)
class ReleaseModule {
    @Provides
    fun applyJitter() = APPLY
}

/**
 * Anything that:
 * - creates an external type (e.g. okhttp)
 * - makes a runtime decision on what type to create
 * - binds a class to multiple types
 * - binds to a generic type
 * needs to be in a Module (i.e. can't just use @Inject).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BortAppModule {
    companion object {
        @Provides
        @Singleton
        fun bortEnabledProvider(
            settingsProvider: SettingsProvider,
            preferenceBortEnabledProvider: Lazy<PreferenceBortEnabledProvider>,
            bortAlwaysEnabledProvider: Lazy<BortAlwaysEnabledProvider>,
        ) = if (settingsProvider.isRuntimeEnableRequired) {
            preferenceBortEnabledProvider.get()
        } else {
            bortAlwaysEnabledProvider.get()
        }

        @Provides
        @Singleton
        fun okHttpClient(
            settingsProvider: SettingsProvider,
            interceptors: InjectSet<Interceptor>,
        ) = OkHttpClient.Builder()
            .connectTimeout(settingsProvider.httpApiSettings.connectTimeout.toJavaDuration())
            .writeTimeout(settingsProvider.httpApiSettings.writeTimeout.toJavaDuration())
            .readTimeout(settingsProvider.httpApiSettings.readTimeout.toJavaDuration())
            .callTimeout(settingsProvider.httpApiSettings.callTimeout.toJavaDuration())
            .also { client -> interceptors.forEach { client.addInterceptor(it) } }
            .build()

        @Provides
        @Singleton
        fun retrofit(settingsProvider: SettingsProvider, okHttpClient: OkHttpClient) = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(settingsProvider.httpApiSettings.filesBaseUrl.toHttpUrl())
            .addConverterFactory(
                kotlinxJsonConverterFactory()
            )
            .build()

        @UploadHoldingArea
        @Provides
        fun holdingAreaPharedPrefs(context: Application) = context.getSharedPreferences(
            FILE_UPLOAD_HOLDING_AREA_PREFERENCE_FILE_NAME,
            Context.MODE_PRIVATE
        )

        @Provides
        fun bundledConfig(resources: Resources) = BundledConfig {
            resources.readBundledSettings()
        }

        @Provides
        fun bootId() = LinuxBootId { File("/proc/sys/kernel/random/boot_id").readText().trim() }

        @Provides
        @BasicCommandTimout
        fun basicTimeout(): Long = BASIC_COMMAND_TIMEOUT_MS

        @Provides
        @Singleton
        @BugReportRequestStore
        fun bugReportRequest(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "bug_report_requests"),
            getMaxBuckets = { settingsProvider.bugReportSettings.rateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.bugReportSettings.rateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindBugReportRequest(@BugReportRequestStore store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @Reboots
        fun reboots(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "reboot_events"),
            getMaxBuckets = { settingsProvider.rebootEventsSettings.rateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.rebootEventsSettings.rateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindReboots(@Reboots store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @BugReportPeriodic
        fun bugReportPeriodic(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "bug_report_periodic"),
            getMaxBuckets = { 1 },
            getTokenBucketFactory = {
                RealTokenBucketFactory(
                    defaultCapacity = 3,
                    defaultPeriod = settingsProvider.bugReportSettings.requestInterval *
                        settingsProvider.bugReportSettings.periodicRateLimitingPercentOfPeriod / 100,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindBugReportPeriodic(@BugReportPeriodic store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @Tombstone
        fun tombstone(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "tombstones"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.tombstonesRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.dropBoxSettings.tombstonesRateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindTombstone(@Tombstone store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @JavaException
        fun javaException(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "java_execeptions"),
            // Note: the backtrace signature is used as key, so one bucket per issue basically.
            getMaxBuckets = { settingsProvider.dropBoxSettings.javaExceptionsRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(
                    rateLimitingSettings = settingsProvider.dropBoxSettings.javaExceptionsRateLimitingSettings,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindJavaException(@JavaException store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @Wtf
        fun wtf(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "wtfs"),
            // Note: the backtrace signature is used as key, so one bucket per issue basically.
            getMaxBuckets = { settingsProvider.dropBoxSettings.wtfsRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(
                    rateLimitingSettings = settingsProvider.dropBoxSettings.wtfsRateLimitingSettings,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindWtf(@Wtf store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @WtfTotal
        fun wtfTotal(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "wtfs_total"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.wtfsTotalRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(
                    rateLimitingSettings = settingsProvider.dropBoxSettings.wtfsTotalRateLimitingSettings,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindWtfTotal(@WtfTotal store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @Anr
        fun anrs(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "anrs"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.anrRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.dropBoxSettings.anrRateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindAnr(@Anr store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @Kmsg
        fun kmsg(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "kmsgs"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.kmsgsRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.dropBoxSettings.kmsgsRateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindKmsg(@Kmsg store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @StructuredLog
        fun structuredLog(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "memfault_structured"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.structuredLogRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.dropBoxSettings.structuredLogRateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindStructuredLog(@StructuredLog store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @KernelOops
        fun kernelOops(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "kernel_oops"),
            getMaxBuckets = { 1 },
            getTokenBucketFactory = {
                val settings = settingsProvider.logcatSettings.kernelOopsRateLimitingSettings
                RealTokenBucketFactory(
                    defaultCapacity = settings.defaultCapacity,
                    defaultPeriod = settings.defaultPeriod.duration,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindOops(@KernelOops store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @Logcat
        fun logcat(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "logcat_periodic"),
            getMaxBuckets = { 1 },
            getTokenBucketFactory = {
                RealTokenBucketFactory(
                    defaultCapacity = 2,
                    defaultPeriod = settingsProvider.logcatSettings.collectionInterval / 2,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindLogcat(@Logcat store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @MetricsCollection
        fun metricCollection(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "metrics_periodic"),
            getMaxBuckets = { 1 },
            getTokenBucketFactory = {
                RealTokenBucketFactory(
                    defaultCapacity = 2,
                    defaultPeriod = settingsProvider.metricsSettings.collectionInterval / 2,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindMetricCollection(@MetricsCollection store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @HighResMetricsFile
        fun highResMetricsFile(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "high_res_metrics"),
            getMaxBuckets = { 1 },
            getTokenBucketFactory = {
                RealTokenBucketFactory(
                    defaultCapacity = 2,
                    defaultPeriod = settingsProvider.metricsSettings.collectionInterval / 2,
                    metrics = metrics,
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindHighResMetricsFile(@HighResMetricsFile store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @MetricReportStore
        fun metricReports(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "memfault_report"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.metricReportRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.dropBoxSettings.metricReportRateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindMetricReport(@MetricReportStore store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @ContinuousLogFile
        fun continuousLogFiles(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "continuous_log"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.continuousLogFileRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(
                    settingsProvider.dropBoxSettings.continuousLogFileRateLimitingSettings,
                    metrics
                )
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindContinuousLogFile(@ContinuousLogFile store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        @MarDropbox
        fun marDropbox(
            application: Application,
            settingsProvider: SettingsProvider,
            metrics: BuiltinMetricsStore,
            devMode: DevMode,
        ): TokenBucketStore = RealTokenBucketStore(
            storage = createFor(application, "mar_file"),
            getMaxBuckets = { settingsProvider.dropBoxSettings.marFileRateLimitingSettings.maxBuckets },
            getTokenBucketFactory = {
                RealTokenBucketFactory.from(settingsProvider.dropBoxSettings.marFileRateLimitingSettings, metrics)
            },
            devMode = devMode,
        )

        @Provides
        @IntoSet
        fun bindMarDropbox(@MarDropbox store: TokenBucketStore): TokenBucketStore = store

        @Provides
        @Singleton
        fun uploadService(retrofit: Retrofit) = retrofit.create(PreparedUploadService::class.java)

        @Provides
        @Singleton
        fun settingsUpdateService(okHttpClient: OkHttpClient, settingsProvider: SettingsProvider) =
            SettingsUpdateService.create(
                okHttpClient = okHttpClient,
                deviceBaseUrl = settingsProvider.httpApiSettings.deviceBaseUrl
            )

        @Provides
        @Singleton
        fun deviceConfigUpdateService(okHttpClient: OkHttpClient, settingsProvider: SettingsProvider) =
            DeviceConfigUpdateService.create(
                okHttpClient = okHttpClient,
                deviceBaseUrl = settingsProvider.httpApiSettings.deviceBaseUrl
            )

        @Provides
        fun dataScrubber(settingsProvider: SettingsProvider): DataScrubber = DataScrubber(
            settingsProvider.dataScrubbingSettings.rules.filterIsInstance(LineScrubbingCleaner::class.java) +
                CustomLogScrubber
        )

        @Provides
        fun kernelOopsDetector(
            settingsProvider: SettingsProvider,
            kernelOopsDetector: KernelOopsDetector,
        ) = if (settingsProvider.logcatSettings.kernelOopsDataSourceEnabled) kernelOopsDetector
        else NoopLogcatLineProcessor

        @Provides
        @MarFileSampledHoldingDir
        fun marFileSampledHoldingDir(application: Application) = File(application.filesDir, "mar-uploads")

        @Provides
        @MarFileUnsampledHoldingDir
        fun marFileUnsampledHoldingDir(application: Application) = File(application.filesDir, "mar-unsampled")
    }

    @Binds
    abstract fun reporterConnector(real: RealReporterServiceConnector): ReporterServiceConnector
}

@Qualifier
@Retention(RUNTIME)
@Target(FIELD, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class UploadHoldingArea

@Qualifier
@Retention(RUNTIME)
@Target(FIELD, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class MarFileSampledHoldingDir

@Qualifier
@Retention(RUNTIME)
@Target(FIELD, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class MarFileUnsampledHoldingDir

/**
 * Injecting Set<T> for multibinding doesn't work in Kotlin, because the kotlin set is typed Set<out T>, so we get
 * a missing binding error. Always use this alias e.g. InjectSet<T> to inject multibinding values, instead.
 */
typealias InjectSet<T> = Set<@JvmSuppressWildcards T>

// typealias InjectMap<K, V> = Map<@JvmSuppressWildcards K, @JvmSuppressWildcards V>
