package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
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
import com.memfault.bort.http.GzipRequestInterceptor
import com.memfault.bort.http.LoggingNetworkInterceptor
import com.memfault.bort.http.ProjectKeyInjectingInterceptor
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.logcat.LogcatCollectionTask
import com.memfault.bort.logcat.LogcatCollector
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.logcat.RealNextLogcatCidProvider
import com.memfault.bort.logcat.RealNextLogcatStartTimeProvider
import com.memfault.bort.logcat.runLogcat
import com.memfault.bort.metrics.BatteryStatsHistoryCollector
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.metrics.RealLastHeartbeatEndTimeProvider
import com.memfault.bort.metrics.RealNextBatteryStatsHistoryStartProvider
import com.memfault.bort.metrics.SharedPreferencesMetricRegistry
import com.memfault.bort.metrics.runBatteryStats
import com.memfault.bort.requester.BugReportRequestWorker
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.requester.LogcatCollectionRequester
import com.memfault.bort.requester.MetricsCollectionRequester
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.ConfigValue
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.PeriodicRequesterRestartTask
import com.memfault.bort.settings.RealStoredSettingsPreferenceProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.SettingsUpdateRequester
import com.memfault.bort.settings.SettingsUpdateService
import com.memfault.bort.settings.SettingsUpdateTask
import com.memfault.bort.settings.StoredSettingsPreferenceProvider
import com.memfault.bort.settings.realSettingsUpdateCallback
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.time.RealBootRelativeTimeProvider
import com.memfault.bort.time.RealCombinedTimeProvider
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.TokenBucketStoreRegistry
import com.memfault.bort.tokenbucket.createAndRegisterStore
import com.memfault.bort.uploader.EnqueueFileUpload
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.FileUploadTask
import com.memfault.bort.uploader.HttpTask
import com.memfault.bort.uploader.HttpTaskCallFactory
import com.memfault.bort.uploader.MemfaultFileUploader
import com.memfault.bort.uploader.PreparedUploadService
import com.memfault.bort.uploader.PreparedUploader
import com.memfault.bort.uploader.enqueueFileUploadTask
import java.io.File
import kotlin.time.toJavaDuration
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
    val reporterServiceConnector: ReporterServiceConnector,
    val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
    val fileUploadHoldingArea: FileUploadHoldingArea,
    val settingsUpdateServiceFactory: () -> SettingsUpdateService,
    val periodicWorkRequesters: List<PeriodicWorkRequester>,
    val tokenBucketStoreRegistry: TokenBucketStoreRegistry,
    val bugReportRequestsTokenBucketStore: TokenBucketStore,
    val rebootEventTokenBucketStore: TokenBucketStore,
    val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    val jitterDelayProvider: JitterDelayProvider,
) {
    open class Builder(
        private val context: Context,
        private val resources: Resources = context.resources,
        private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
            context
        ),
        private val metricsSharedPreferences: SharedPreferences = context.getSharedPreferences(
            METRICS_PREFERENCE_FILE_NAME,
            Context.MODE_PRIVATE
        ),
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
        var jitterDelayProvider: JitterDelayProvider? = null

        fun build(): AppComponents {
            val storedSettingsPreferenceProvider = RealStoredSettingsPreferenceProvider(
                sharedPreferences = sharedPreferences,
                getBundledConfig = {
                    resources.assets
                        .open(DEFAULT_SETTINGS_ASSET_FILENAME)
                        .use {
                            it.bufferedReader().readText()
                        }
                }
            )
            val settingsProvider = settingsProvider ?: DynamicSettingsProvider(storedSettingsPreferenceProvider)
            val bortEnabledProvider =
                bortEnabledProvider ?: if (settingsProvider.isRuntimeEnableRequired) {
                    PreferenceBortEnabledProvider(
                        sharedPreferences,
                        defaultValue = !settingsProvider.isRuntimeEnableRequired
                    )
                } else {
                    BortAlwaysEnabledProvider()
                }
            val deviceIdProvider = deviceIdProvider ?: RandomUuidDeviceIdProvider(sharedPreferences)
            val deviceInfoProvider = RealDeviceInfoProvider(settingsProvider.deviceInfoSettings)
            val fileUploaderFactory =
                fileUploaderFactory ?: MemfaultFileUploaderFactory()
            val projectKeyInjectingInterceptor = ProjectKeyInjectingInterceptor(
                settingsProvider.httpApiSettings::projectKey
            )
            val okHttpClient = okHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(settingsProvider.httpApiSettings.connectTimeout.toJavaDuration())
                .writeTimeout(settingsProvider.httpApiSettings.writeTimeout.toJavaDuration())
                .readTimeout(settingsProvider.httpApiSettings.readTimeout.toJavaDuration())
                .callTimeout(settingsProvider.httpApiSettings.callTimeout.toJavaDuration())
                .addInterceptor(projectKeyInjectingInterceptor)
                .addInterceptor(
                    debugInfoInjectingInterceptor ?: DebugInfoInjectingInterceptor(
                        settingsProvider.sdkVersionInfo,
                        deviceIdProvider,
                        bortEnabledProvider,
                        deviceInfoProvider,
                    )
                )
                .addInterceptor(loggingInterceptor ?: LoggingNetworkInterceptor())
                .addInterceptor(GzipRequestInterceptor())
                .build()
            val retrofit = retrofit ?: Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(settingsProvider.httpApiSettings.filesBaseUrl.toHttpUrl())
                .addConverterFactory(
                    kotlinxJsonConverterFactory()
                )
                .build()
            val reporterServiceConnector = reporterServiceConnector ?: RealReporterServiceConnector(
                context = context,
                inboundLooper = Looper.getMainLooper()
            )

            val temporaryFileFactory = object : TemporaryFileFactory {
                override val temporaryFileDirectory: File = context.cacheDir
            }
            val bootRelativeTimeProvider = RealBootRelativeTimeProvider(context)
            val packageManagerClient = PackageManagerClient(
                reporterServiceConnector = reporterServiceConnector,
                commandTimeoutConfig = settingsProvider.packageManagerSettings::commandTimeout
            )
            val jitterDelayProvider = jitterDelayProvider ?: JitterDelayProvider(applyJitter = true)
            val enqueueFileUpload: EnqueueFileUpload = { file, metadata, debugTag ->
                enqueueFileUploadTask(
                    context = context,
                    file = file,
                    payload = metadata,
                    getUploadConstraints = settingsProvider.httpApiSettings::uploadConstraints,
                    debugTag = debugTag,
                    jitterDelayProvider = jitterDelayProvider
                )
            }
            val builtinMetricsStore = BuiltinMetricsStore(
                registry = SharedPreferencesMetricRegistry(metricsSharedPreferences)
            )
            val fileUploadHoldingArea = FileUploadHoldingArea(
                sharedPreferences = context.getSharedPreferences(
                    FILE_UPLOAD_HOLDING_AREA_PREFERENCE_FILE_NAME,
                    Context.MODE_PRIVATE
                ),
                enqueueFileUpload = enqueueFileUpload,
                resetEventTimeout = { FileUploadHoldingAreaTimeoutTask.reschedule(context) },
                getTrailingMargin = settingsProvider.fileUploadHoldingAreaSettings::trailingMargin,
                getEventOfInterestTTL = settingsProvider.logcatSettings::collectionInterval,
                getMaxStoredEventsOfInterest = settingsProvider
                    .fileUploadHoldingAreaSettings::maxStoredEventsOfInterest,
            )

            val tokenBucketStoreRegistry = TokenBucketStoreRegistry()
            val bugReportRequestsTokenBucketStore = tokenBucketStoreRegistry.createAndRegisterStore(
                context, "bug_report_requests"
            ) { storage ->
                TokenBucketStore(
                    storage = storage,
                    getMaxBuckets = { settingsProvider.bugReportSettings.rateLimitingSettings.maxBuckets },
                    getTokenBucketFactory = {
                        RealTokenBucketFactory.from(settingsProvider.bugReportSettings.rateLimitingSettings)
                    },
                )
            }
            val rebootEventTokenBucketStore = tokenBucketStoreRegistry.createAndRegisterStore(
                context, "reboot_events"
            ) { storage ->
                TokenBucketStore(
                    storage = storage,
                    getMaxBuckets = { settingsProvider.rebootEventsSettings.rateLimitingSettings.maxBuckets },
                    getTokenBucketFactory = {
                        RealTokenBucketFactory.from(settingsProvider.rebootEventsSettings.rateLimitingSettings)
                    },
                )
            }

            val packageNameAllowList = RuleBasedPackageNameAllowList(
                rulesConfig = {
                    settingsProvider.dataScrubbingSettings.rules.filterIsInstance(AndroidAppIdScrubbingRule::class.java)
                }
            )

            val dataScrubber = {
                DataScrubber(
                    settingsProvider.dataScrubbingSettings.rules.filterIsInstance(LineScrubbingCleaner::class.java),
                )
            }

            val dropBoxSettings = settingsProvider.dropBoxSettings
            val nextLogcatCidProvider = RealNextLogcatCidProvider(sharedPreferences)
            val dropBoxEntryProcessors = realDropBoxEntryProcessors(
                tempFileFactory = temporaryFileFactory,
                bootRelativeTimeProvider = bootRelativeTimeProvider,
                enqueueFileUpload = enqueueFileUpload,
                nextLogcatCidProvider = nextLogcatCidProvider,
                packageManagerClient = packageManagerClient,
                deviceInfoProvider = deviceInfoProvider,
                builtinMetricsStore = builtinMetricsStore,
                handleEventOfInterest = fileUploadHoldingArea::handleEventOfInterest,
                tombstoneTokenBucketStore = tokenBucketStoreRegistry.createAndRegisterStore(
                    context, "tombstones"
                ) { storage ->
                    TokenBucketStore(
                        storage = storage,
                        getMaxBuckets = { dropBoxSettings.tombstonesRateLimitingSettings.maxBuckets },
                        getTokenBucketFactory = {
                            RealTokenBucketFactory.from(dropBoxSettings.tombstonesRateLimitingSettings)
                        },
                    )
                },
                javaExceptionTokenBucketStore = tokenBucketStoreRegistry.createAndRegisterStore(
                    context, "java_execeptions"
                ) { storage ->
                    TokenBucketStore(
                        storage = storage,
                        // Note: the backtrace signature is used as key, so one bucket per issue basically.
                        getMaxBuckets = { dropBoxSettings.javaExceptionsRateLimitingSettings.maxBuckets },
                        getTokenBucketFactory = {
                            RealTokenBucketFactory.from(dropBoxSettings.javaExceptionsRateLimitingSettings)
                        },
                    )
                },
                anrTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "anrs") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { dropBoxSettings.anrRateLimitingSettings.maxBuckets },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory.from(dropBoxSettings.anrRateLimitingSettings)
                            },
                        )
                    },
                kmsgTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "kmsgs") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { dropBoxSettings.kmsgsRateLimitingSettings.maxBuckets },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory.from(dropBoxSettings.kmsgsRateLimitingSettings)
                            },
                        )
                    },
                structuredLogTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "memfault_structured") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { dropBoxSettings.structuredLogRateLimitingSettings.maxBuckets },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory.from(dropBoxSettings.structuredLogRateLimitingSettings)
                            },
                        )
                    },
                packageNameAllowList = packageNameAllowList,
                combinedTimeProvider = RealCombinedTimeProvider(context),
            ) + extraDropBoxEntryProcessors

            val pendingBugReportRequestAccessor = PendingBugReportRequestAccessor(
                storage = RealPendingBugReportRequestStorage(sharedPreferences),
            )

            val settingsUpdateServiceFactory = {
                SettingsUpdateService.create(
                    okHttpClient = okHttpClient,
                    deviceBaseUrl = settingsProvider.httpApiSettings.deviceBaseUrl
                )
            }

            val periodicWorkRequesters = listOf(
                SettingsUpdateRequester(
                    context = context,
                    httpApiSettings = settingsProvider.httpApiSettings,
                    getUpdateInterval = settingsProvider::settingsUpdateInterval,
                    jitterDelayProvider = jitterDelayProvider
                ),
                MetricsCollectionRequester(context, settingsProvider.metricsSettings),
                BugReportRequester(context, settingsProvider.bugReportSettings),
                LogcatCollectionRequester(context, settingsProvider.logcatSettings),
            )

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
                nextLogcatCidProvider = nextLogcatCidProvider,
                temporaryFileFactory = temporaryFileFactory,
                pendingBugReportRequestAccessor = pendingBugReportRequestAccessor,
                builtinMetricsStore = builtinMetricsStore,
                fileUploadHoldingArea = fileUploadHoldingArea,
                settingsUpdateServiceFactory = settingsUpdateServiceFactory,
                deviceInfoProvider = deviceInfoProvider,
                storedSettingsPreferenceProvider = storedSettingsPreferenceProvider,
                periodicWorkRequesters = periodicWorkRequesters,
                // Rate limiting for periodic tasks:
                // The periodic tasks are driven by the WorkManager and should be low frequency (> 15 min intervals), but
                // when the SDK is disabled/re-enabled or when dynamic settings change, the tasks are restarted and immediately
                // run. The period is half the task's interval, to make it highly unlikely the bucket will be empty when the
                // task is run under normal conditions.
                bugReportPeriodicTaskTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "bug_report_periodic") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { 1 },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory(
                                    defaultCapacity = 2,
                                    defaultPeriod = settingsProvider.bugReportSettings.requestInterval / 2,
                                )
                            },
                        )
                    },
                logcatPeriodicTaskTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "logcat_periodic") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { 1 },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory(
                                    defaultCapacity = 2,
                                    defaultPeriod = settingsProvider.logcatSettings.collectionInterval / 2,
                                )
                            },
                        )
                    },
                metricsPeriodicTaskTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "metrics_periodic") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { 1 },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory(
                                    defaultCapacity = 2,
                                    defaultPeriod = settingsProvider.metricsSettings.collectionInterval / 2,
                                )
                            },
                        )
                    },
                settingsUpdatePeriodicTaskTokenBucketStore =
                    tokenBucketStoreRegistry.createAndRegisterStore(context, "settings_update_periodic") { storage ->
                        TokenBucketStore(
                            storage = storage,
                            getMaxBuckets = { 1 },
                            getTokenBucketFactory = {
                                RealTokenBucketFactory(
                                    defaultCapacity = 2,
                                    defaultPeriod = settingsProvider.settingsUpdateInterval / 2,
                                )
                            },
                        )
                    },
                dataScrubber = dataScrubber,
                packageNameAllowList = packageNameAllowList,
                packageManagerClient = packageManagerClient,
                interceptingFactory = interceptingWorkerFactory,
            )

            val httpTaskCallFactory = HttpTaskCallFactory.fromContextAndConstraints(
                context = context,
                getUploadConstraints = settingsProvider.httpApiSettings::uploadConstraints,
                projectKeyInjectingInterceptor = projectKeyInjectingInterceptor,
                jitterDelayProvider = jitterDelayProvider
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
                reporterServiceConnector = reporterServiceConnector,
                pendingBugReportRequestAccessor = pendingBugReportRequestAccessor,
                fileUploadHoldingArea = fileUploadHoldingArea,
                settingsUpdateServiceFactory = settingsUpdateServiceFactory,
                periodicWorkRequesters = periodicWorkRequesters,
                tokenBucketStoreRegistry = tokenBucketStoreRegistry,
                bugReportRequestsTokenBucketStore = bugReportRequestsTokenBucketStore,
                rebootEventTokenBucketStore = rebootEventTokenBucketStore,
                storedSettingsPreferenceProvider = storedSettingsPreferenceProvider,
                jitterDelayProvider = jitterDelayProvider,
            )
        }
    }

    fun isEnabled(): Boolean = bortEnabledProvider.isEnabled()
}

@OptIn(ExperimentalSerializationApi::class)
fun kotlinxJsonConverterFactory(): Converter.Factory =
    BortJson.asConverterFactory("application/json".toMediaType())

interface InterceptingWorkerFactory {
    fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
        settingsProvider: SettingsProvider,
        reporterServiceConnector: ReporterServiceConnector,
        pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
        bugReportPeriodicTaskTokenBucketStore: TokenBucketStore,
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
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
    private val builtinMetricsStore: BuiltinMetricsStore,
    private val fileUploadHoldingArea: FileUploadHoldingArea,
    private val settingsUpdateServiceFactory: () -> SettingsUpdateService,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    private val periodicWorkRequesters: List<PeriodicWorkRequester>,
    private val bugReportPeriodicTaskTokenBucketStore: TokenBucketStore,
    private val logcatPeriodicTaskTokenBucketStore: TokenBucketStore,
    private val metricsPeriodicTaskTokenBucketStore: TokenBucketStore,
    private val settingsUpdatePeriodicTaskTokenBucketStore: TokenBucketStore,
    private val dataScrubber: ConfigValue<DataScrubber>,
    private val packageNameAllowList: PackageNameAllowList,
    private val packageManagerClient: PackageManagerClient,
    private val interceptingFactory: InterceptingWorkerFactory? = null,
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
            reporterServiceConnector,
            pendingBugReportRequestAccessor,
            bugReportPeriodicTaskTokenBucketStore,
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
            BugReportRequestWorker::class.qualifiedName -> BugReportRequestWorker(
                appContext = appContext,
                workerParameters = workerParameters,
                pendingBugReportRequestAccessor = pendingBugReportRequestAccessor,
                tokenBucketStore = bugReportPeriodicTaskTokenBucketStore,
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
                getMaxAttempts = { settingsProvider.bugReportSettings.maxUploadAttempts },
                getUploadCompressionEnabled = { settingsProvider.httpApiSettings.uploadCompressionEnabled },
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
                    timeoutConfig = settingsProvider.batteryStatsSettings::commandTimeout,
                ),
                enqueueFileUpload = enqueueFileUpload,
                nextLogcatCidProvider = nextLogcatCidProvider,
                combinedTimeProvider = RealCombinedTimeProvider(context),
                lastHeartbeatEndTimeProvider = RealLastHeartbeatEndTimeProvider(sharedPreferences),
                deviceInfoProvider = RealDeviceInfoProvider(settingsProvider.deviceInfoSettings),
                builtinMetricsStore = builtinMetricsStore,
                tokenBucketStore = metricsPeriodicTaskTokenBucketStore,
            )
            BugReportRequestTimeoutTask::class.qualifiedName -> BugReportRequestTimeoutTask(
                context = context,
                pendingBugReportRequestAccessor = pendingBugReportRequestAccessor,
            )
            LogcatCollectionTask::class.qualifiedName -> LogcatCollectionTask(
                logcatSettings = settingsProvider.logcatSettings,
                logcatCollector = LogcatCollector(
                    temporaryFileFactory = temporaryFileFactory,
                    nextLogcatStartTimeProvider = RealNextLogcatStartTimeProvider(sharedPreferences),
                    nextLogcatCidProvider = RealNextLogcatCidProvider(sharedPreferences),
                    runLogcat = reporterServiceConnector::runLogcat,
                    filterSpecsConfig = settingsProvider.logcatSettings::filterSpecs,
                    dataScrubber = dataScrubber,
                    timeoutConfig = settingsProvider.logcatSettings::commandTimeout,
                    packageNameAllowList = packageNameAllowList,
                    packageManagerClient = packageManagerClient,
                ),
                fileUploadHoldingArea = fileUploadHoldingArea,
                combinedTimeProvider = RealCombinedTimeProvider(context),
                deviceInfoProvider = RealDeviceInfoProvider(settingsProvider.deviceInfoSettings),
                tokenBucketStore = logcatPeriodicTaskTokenBucketStore,
            )
            FileUploadHoldingAreaTimeoutTask::class.qualifiedName -> FileUploadHoldingAreaTimeoutTask(
                fileUploadHoldingArea = fileUploadHoldingArea,
            )
            SettingsUpdateTask::class.qualifiedName -> SettingsUpdateTask(
                deviceInfoProvider = deviceInfoProvider,
                settingsUpdateServiceFactory = settingsUpdateServiceFactory,
                settingsProvider = settingsProvider,
                storedSettingsPreferenceProvider = storedSettingsPreferenceProvider,
                settingsUpdateCallback = realSettingsUpdateCallback(
                    context,
                    reporterServiceConnector,
                ),
                tokenBucketStore = settingsUpdatePeriodicTaskTokenBucketStore,
            )
            PeriodicRequesterRestartTask::class.qualifiedName -> PeriodicRequesterRestartTask(
                getMaxAttempts = { 1 },
                periodicWorkRequesters = periodicWorkRequesters,
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
