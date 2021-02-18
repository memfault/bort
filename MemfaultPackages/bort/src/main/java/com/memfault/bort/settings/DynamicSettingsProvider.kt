package com.memfault.bort.settings

import com.memfault.bort.BuildConfig
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.BuildConfigSdkVersionInfo
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.Logger
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * A SettingsProvider that reads from the prefetched settings file in
 * the assets folder or from a shared preferences entry that contains
 * remotely fetched settings.
 */
open class DynamicSettingsProvider(
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
) : SettingsProvider {
    private val settingsCache = CachedProperty {
        storedSettingsPreferenceProvider.get()
    }

    private val settings by settingsCache

    override val minLogLevel: LogLevel
        get() = LogLevel.fromInt(settings.bortMinLogLevel) ?: LogLevel.VERBOSE

    override val isRuntimeEnableRequired: Boolean = BuildConfig.RUNTIME_ENABLE_REQUIRED

    override val settingsUpdateInterval: Duration
        get() = settings.bortSettingsUpdateInterval.duration

    override val httpApiSettings = object : HttpApiSettings {
        override val uploadNetworkConstraint: NetworkConstraint
            get() = if (settings.httpApiUploadNetworkConstraintAllowMeteredConnection) NetworkConstraint.CONNECTED
            else NetworkConstraint.UNMETERED
        override val uploadCompressionEnabled
            get() = settings.httpApiUploadCompressionEnabled
        override val projectKey = BuildConfig.MEMFAULT_PROJECT_API_KEY
        override val filesBaseUrl
            get() = settings.httpApiFilesBaseUrl
        override val deviceBaseUrl
            get() = settings.httpApiDeviceBaseUrl
        override val ingressBaseUrl
            get() = settings.httpApiIngressBaseUrl
    }

    override val deviceInfoSettings = object : DeviceInfoSettings {
        override val androidBuildFormat
            get() = AndroidBuildFormat.getById(settings.deviceInfoAndroidBuildVersionSource)
        override val androidBuildVersionKey
            get() = settings.deviceInfoAndroidBuildVersionKey
        override val androidHardwareVersionKey
            get() = settings.deviceInfoAndroidHardwareVersionKey
        override val androidSerialNumberKey
            get() = settings.deviceInfoAndroidDeviceSerialKey
    }

    override val sdkVersionInfo = BuildConfigSdkVersionInfo

    override val bugReportSettings = object : BugReportSettings {
        override val dataSourceEnabled
            get() = settings.bugReportDataSourceEnabled
        override val requestInterval
            get() = settings.bugReportCollectionInterval.duration
        override val defaultOptions
            get() = BugReportOptions(minimal = settings.bugReportOptionsMinimal)
        override val maxUploadAttempts
            get() = settings.bugReportMaxUploadAttempts
        override val firstBugReportDelayAfterBoot
            get() = settings.bugReportFirstBugReportDelayAfterBoot.duration
        override val rateLimitingSettings: RateLimitingSettings
            get() = settings.bugReportRequestRateLimitingSettings
    }

    override val dropBoxSettings = object : DropBoxSettings {
        override val dataSourceEnabled
            get() = settings.dropBoxDataSourceEnabled
        override val anrRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxAnrsRateLimitingSettings
        override val javaExceptionsRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxJavaExceptionsRateLimitingSettings
        override val kmsgsRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxKmsgsRateLimitingSettings
        override val tombstonesRateLimitingSettings: RateLimitingSettings
            get() = settings.dropBoxTombstonesRateLimitingSettings
    }

    override val metricsSettings = object : MetricsSettings {
        override val dataSourceEnabled
            get() = settings.metricsDataSourceEnabled
        override val collectionInterval
            get() = settings.metricsCollectionInterval.duration
    }

    override val batteryStatsSettings = object : BatteryStatsSettings {
        override val dataSourceEnabled
            get() = settings.batteryStatsDataSourceEnabled
    }

    override val logcatSettings = object : LogcatSettings {
        override val dataSourceEnabled
            get() = settings.logcatDataSourceEnabled
        override val collectionInterval
            get() = settings.logcatCollectionInterval.duration
        override val filterSpecs: List<LogcatFilterSpec>
            get() = settings.logcatFilterSpecs
    }

    override val fileUploadHoldingAreaSettings = object : FileUploadHoldingAreaSettings {
        override val trailingMargin
            get() = settings.fileUploadHoldingAreaTrailingMargin.duration
        override val maxStoredEventsOfInterest
            get() = settings.fileUploadHoldingAreaMaxStoredEventsOfInterest
    }

    override val rebootEventsSettings = object : RebootEventsSettings {
        override val rateLimitingSettings: RateLimitingSettings
            get() = settings.rebootEventsRateLimitingSettings
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
