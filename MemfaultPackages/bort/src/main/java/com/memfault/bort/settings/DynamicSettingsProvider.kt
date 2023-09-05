package com.memfault.bort.settings

import com.memfault.bort.BuildConfig
import com.memfault.bort.DataScrubbingRule
import com.memfault.bort.DevMode
import com.memfault.bort.DumpsterCapabilities
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.isEnabled
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.settings.NetworkConstraint.CONNECTED
import com.memfault.bort.settings.NetworkConstraint.UNMETERED
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * A SettingsProvider that reads from the prefetched settings file in
 * the assets folder or from a shared preferences entry that contains
 * remotely fetched settings.
 *
 * Any settings overrides based on other settings, or e.g. dev mode should ideally be done here.
 */
@Singleton
@ContributesBinding(scope = SingletonComponent::class)
open class DynamicSettingsProvider @Inject constructor(
    private val storedSettingsPreferenceProvider: ReadonlyFetchedSettingsProvider,
    private val dumpsterCapabilities: DumpsterCapabilities,
    private val cachedClientServerMode: CachedClientServerMode,
    private val devMode: DevMode,
    private val projectKeyProvider: ProjectKeyProvider,
) : SettingsProvider {
    @Transient
    private val settingsCache = CachedProperty {
        storedSettingsPreferenceProvider.get()
    }

    private val settings by settingsCache

    override val minLogcatLevel: LogLevel
        get() = LogLevel.fromInt(settings.bortMinLogcatLevel) ?: LogLevel.VERBOSE

    override val minStructuredLogLevel: LogLevel
        get() = LogLevel.fromInt(settings.bortMinStructuredLogLevel) ?: LogLevel.INFO

    override val eventLogEnabled: Boolean
        get() = settings.bortEventLogEnabled

    override val internalLogToDiskEnabled: Boolean
        get() = settings.bortInternalLogToDiskEnabled

    override val isRuntimeEnableRequired: Boolean = BuildConfig.RUNTIME_ENABLE_REQUIRED

    override val settingsUpdateInterval: Duration
        get() = settings.bortSettingsUpdateInterval.duration

    override val httpApiSettings = object : HttpApiSettings {
        override val uploadNetworkConstraint: NetworkConstraint
            get() = if (settings.httpApiUploadNetworkConstraintAllowMeteredConnection) CONNECTED
            else UNMETERED
        override val uploadCompressionEnabled
            get() = settings.httpApiUploadCompressionEnabled
        override val projectKey get() = projectKeyProvider.projectKey
        override val filesBaseUrl
            get() = settings.httpApiFilesBaseUrl
        override val deviceBaseUrl
            get() = settings.httpApiDeviceBaseUrl
        override val ingressBaseUrl
            get() = settings.httpApiIngressBaseUrl
        override val connectTimeout
            get() = settings.httpApiConnectTimeout.duration
        override val writeTimeout
            get() = settings.httpApiWriteTimeout.duration
        override val readTimeout
            get() = settings.httpApiReadTimeout.duration
        override val callTimeout
            get() = settings.httpApiCallTimeout.duration
        override val zipCompressionLevel: Int
            get() = settings.httpApiZipCompressionLevel
        override val batchMarUploads: Boolean
            get() = settings.httpApiBatchMarUploads && !devMode.isEnabled()
        override val batchedMarUploadPeriod: Duration
            get() = settings.httpApiBatchedMarUploadPeriod.duration
        // TODO also true if fleet sampling enabled, but we don't know that
        // Only device config is supported in client/server mode.
        override suspend fun useDeviceConfig(): Boolean =
            cachedClientServerMode.isEnabled() || settings.httpApiUseDeviceConfig
        override val deviceConfigInterval: Duration
            get() = settings.httpApiDeviceConfigInterval.duration
        override val maxMarFileSizeBytes: Int
            get() = settings.httpApiMaxMarFileSizeBytes
        override val maxMarStorageBytes: Long
            get() = settings.httpApiMaxMarStorageBytes
        override val maxMarUnsampledStoredAge: Duration
            get() = settings.httpApiMarUnsampledMaxStoredAge.duration
        override val maxMarUnsampledStoredBytes: Long
            get() = settings.httpApiMaxMarUnsampledStorageBytes
    }

    override val deviceInfoSettings = settings.deviceInfoSettings()

    override val sdkVersionInfo = BuildConfigSdkVersionInfo

    override val bugReportSettings = object : BugReportSettings {
        override val dataSourceEnabled
            get() = settings.bugReportDataSourceEnabled
        override val requestInterval
            get() = settings.bugReportCollectionInterval.duration
        override val defaultOptions
            get() = BugReportOptions(minimal = settings.bugReportOptionsMinimal)
        override val maxStorageBytes: Int
            get() = settings.bugReportMaxStorageBytes
        override val maxStoredAge: Duration
            get() = settings.bugReportMaxStoredAge.duration
        override val maxUploadAttempts
            get() = settings.bugReportMaxUploadAttempts
        override val firstBugReportDelayAfterBoot
            get() = settings.bugReportFirstBugReportDelayAfterBoot.duration
        override val rateLimitingSettings: RateLimitingSettings
            get() = settings.bugReportRequestRateLimitingSettings
        override val periodicRateLimitingPercentOfPeriod: Int
            get() = settings.bugReportPeriodicRateLimitingPercentOfPeriod
    }

    override val dropBoxSettings = object : DropBoxSettings {
        override val dataSourceEnabled
            get() = settings.dropBoxDataSourceEnabled
        override val anrRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxAnrsRateLimitingSettings
        override val javaExceptionsRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxJavaExceptionsRateLimitingSettings
        override val wtfsRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxWtfsRateLimitingSettings
        override val wtfsTotalRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxWtfsTotalRateLimitingSettings
        override val kmsgsRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxKmsgsRateLimitingSettings
        override val structuredLogRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxStructuredLogRateLimitingSettings
        override val tombstonesRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxTombstonesRateLimitingSettings
        override val metricReportRateLimitingSettings: RateLimitingSettings
            get() = settings.metricReportRateLimitingSettings
        override val marFileRateLimitingSettings: RateLimitingSettings
            get() = settings.marFileRateLimitingSettings
        override val continuousLogFileRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxContinuousLogFileLimitingSettings
        override val excludedTags: Set<String>
            get() = settings.dropBoxExcludedTags
        override val scrubTombstones: Boolean
            get() = settings.dropBoxScrubTombstones
    }

    override val metricsSettings = object : MetricsSettings {
        override val dataSourceEnabled
            get() = settings.metricsDataSourceEnabled
        override val collectionInterval
            get() = settings.metricsCollectionInterval.duration
        override val systemProperties: List<String>
            get() = settings.metricsSystemProperties
        override val appVersions: List<String>
            get() = settings.metricsAppVersions
        override val maxNumAppVersions: Int
            get() = settings.metricsMaxNumAppVersions
        override val reporterCollectionInterval: Duration
            get() = settings.metricsReporterCollectionInterval.duration
        override val propertiesUseMetricService: Boolean
            get() = settings.metricsPropertiesUseService
    }

    override val batteryStatsSettings = object : BatteryStatsSettings {
        override val dataSourceEnabled
            get() = settings.batteryStatsDataSourceEnabled
        override val commandTimeout: Duration
            get() = settings.batteryStatsCommandTimeout.duration
        override val useHighResTelemetry: Boolean
            get() = settings.highResTelemetryEnabled && settings.batteryStatsUseHrt
        override val collectSummary: Boolean
            get() = settings.batteryStatsCollectSummary
    }

    override val logcatSettings = object : LogcatSettings {
        override val dataSourceEnabled
            get() = settings.logcatDataSourceEnabled
        override val collectionInterval
            get() = settings.logcatCollectionInterval.duration
        override val commandTimeout: Duration
            get() = settings.logcatCommandTimeout.duration
        override val filterSpecs: List<LogcatFilterSpec>
            get() = settings.logcatFilterSpecs
        override val kernelOopsDataSourceEnabled
            get() = settings.logcatKernelOopsDataSourceEnabled
        override val kernelOopsRateLimitingSettings
            get() = settings.logcatKernelOopsRateLimitingSettings
        override val storeUnsampled: Boolean
            get() = settings.logcatStoreUnsampled
        override val collectionMode
            // Don't enable continuous logging mode unless the service supports it (because this disables periodic
            // logging).
            get() = if (dumpsterCapabilities.supportsContinuousLogging()) settings.logcatCollectionMode else PERIODIC
        override val continuousLogDumpThresholdBytes: Int
            get() = settings.logcatContinuousDumpThresholdBytes
        override val continuousLogDumpThresholdTime: Duration
            get() = settings.logcatContinuousDumpThresholdTime.duration
        override val continuousLogDumpWrappingTimeout: Duration
            get() = settings.logcatContinuousDumpWrappingTimeout.duration
    }

    override val fileUploadHoldingAreaSettings = object : FileUploadHoldingAreaSettings {
        override val trailingMargin
            get() = settings.fileUploadHoldingAreaTrailingMargin.duration
        override val maxStoredEventsOfInterest
            get() = settings.fileUploadHoldingAreaMaxStoredEventsOfInterest
    }

    override val rebootEventsSettings = object : RebootEventsSettings {
        override val dataSourceEnabled: Boolean
            get() = settings.rebootEventsDataSourceEnabled
        override val rateLimitingSettings: RateLimitingSettings
            get() = settings.rebootEventsRateLimitingSettings
    }

    override val selinuxViolationSettings = object : SelinuxViolationSettings {
        override val dataSourceEnabled: Boolean
            get() = settings.selinuxViolationEventsDataSourceEnabled
        override val rateLimitingSettings: RateLimitingSettings
            get() = settings.selinuxViolationEventsRateLimitingSettings
    }

    override val dataScrubbingSettings = object : DataScrubbingSettings {
        override val rules: List<DataScrubbingRule>
            get() = settings.dataScrubbingRules
    }

    override val packageManagerSettings = object : PackageManagerSettings {
        override val commandTimeout: Duration
            get() = settings.packageManagerCommandTimeout.duration
    }

    override val structuredLogSettings = object : StructuredLogSettings {
        override val dataSourceEnabled: Boolean
            get() = settings.structuredLogDataSourceEnabled
        override val rateLimitingSettings: RateLimitingSettings
            get() = settings.structuredLogRateLimitingSettings
        override val dumpPeriod: Duration
            get() = settings.structuredLogDumpPeriod.duration
        override val numEventsBeforeDump: Long
            get() = settings.structuredLogNumEventsBeforeDump
        override val maxMessageSizeBytes: Long
            get() = settings.structuredLogMaxMessageSizeBytes
        override val minStorageThresholdBytes: Long
            get() = settings.structuredLogMinStorageThresholdBytes
        override val metricsReportEnabled: Boolean
            get() = settings.metricReportEnabled
        override val highResMetricsEnabled: Boolean
            get() = settings.highResTelemetryEnabled
    }

    override val otaSettings = object : OtaSettings {
        override val updateCheckInterval: Duration
            get() = settings.otaUpdateCheckInterval.duration
        override val downloadNetworkConstraint: NetworkConstraint
            get() = if (settings.otaDownloadNetworkConstraintAllowMeteredConnection) CONNECTED
            else UNMETERED
    }

    override val storageSettings = object : StorageSettings {
        override val maxClientServerFileTransferStorageBytes: Long
            get() = settings.storageMaxClientServerFileTransferStorageBytes
        override val maxClientServerFileTransferStorageAge: Duration
            get() = settings.storageMaxClientServerFileTransferStorageAge.duration
        override val usageReporterTempMaxStorageBytes: Long
            get() = settings.storageUsageReporterTempMaxStorageBytes
        override val usageReporterTempMaxStorageAge: Duration
            get() = settings.storageUsageReporterTempMaxStorageAge.duration
        override val bortTempMaxStorageBytes: Long
            get() = settings.storageBortTempMaxStorageBytes
        override val bortTempMaxStorageAge: Duration
            get() = settings.storageBortTempMaxStorageAge.duration
    }

    override val fleetSamplingSettings = object : FleetSamplingSettings {
        override val loggingActive: Boolean
            get() = settings.fleetSamplingLoggingActive
        override val debuggingActive: Boolean
            get() = settings.fleetSamplingDebuggingActive
        override val monitoringActive: Boolean
            get() = settings.fleetSamplingMonitroringActive
    }

    override fun invalidate() {
        Logger.d("settings invalidating")
        settingsCache.invalidate()
    }
}

class CachedProperty<out T>(val factory: () -> T) : ReadOnlyProperty<Any, T> {
    private var value: CachedValue<T> = CachedValue.Absent

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return when (val cached = value) {
            is CachedValue.Value -> cached.value
            CachedValue.Absent -> factory().also { value = CachedValue.Value(it) }
        }
    }

    fun invalidate() {
        value = CachedValue.Absent
    }

    private sealed class CachedValue<out T> {
        object Absent : CachedValue<Nothing>()
        class Value<T>(val value: T) : CachedValue<T>()
    }
}
