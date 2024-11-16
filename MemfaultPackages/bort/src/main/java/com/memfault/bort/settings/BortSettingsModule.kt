package com.memfault.bort.settings

import androidx.work.Constraints
import com.memfault.bort.AndroidAppIdScrubbingRule
import com.memfault.bort.logcat.Logs2MetricsRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

fun interface MetricReportEnabled : () -> Boolean
fun interface StructuredLogEnabled : () -> Boolean
fun interface HighResMetricsEnabled : () -> Boolean
fun interface DailyHeartbeatEnabled : () -> Boolean
fun interface UploadCompressionEnabled : () -> Boolean
fun interface BatchMarUploads : () -> Boolean
fun interface ProjectKey : () -> String
fun interface TimeoutConfig : () -> Duration
fun interface RulesConfig : () -> List<AndroidAppIdScrubbingRule>
fun interface UploadConstraints : () -> Constraints
fun interface LogcatCollectionInterval : () -> Duration
fun interface MetricsCollectionInterval : () -> Duration
fun interface MaxUploadAttempts : () -> Int
fun interface MaxMarStorageBytes : () -> Long
fun interface ZipCompressionLevel : () -> Int
fun interface MarUnsampledMaxStorageAge : () -> Duration
fun interface MarUnsampledMaxStorageBytes : () -> Long
fun interface DropboxScrubTombstones : () -> Boolean
fun interface CachePackageManagerReport : () -> Boolean
fun interface DropBoxForceEnableWtfTags : () -> Boolean
fun interface OperationalCrashesExclusions : () -> List<String>
fun interface MetricsPollingIntervalFlow : () -> Flow<Duration>
fun interface Logs2MetricsRules : () -> List<Logs2MetricsRule>

@Module
@InstallIn(SingletonComponent::class)
abstract class BortSettingsModule {
    companion object {
        @Provides
        fun rulesConfig(settings: SettingsProvider) =
            RulesConfig { settings.dataScrubbingSettings.rules.filterIsInstance<AndroidAppIdScrubbingRule>() }

        @Provides
        fun uploadCompression(settings: SettingsProvider) =
            UploadCompressionEnabled { settings.httpApiSettings.uploadCompressionEnabled }

        @Provides
        fun maxUploadAttempts(settings: SettingsProvider) =
            MaxUploadAttempts { settings.bugReportSettings.maxUploadAttempts }

        @Provides
        fun batchMarUploads(settings: SettingsProvider) =
            BatchMarUploads { settings.httpApiSettings.batchMarUploads }

        @Provides
        fun structuredLog(settings: SettingsProvider) =
            StructuredLogEnabled { settings.structuredLogSettings.dataSourceEnabled }

        @Provides
        fun highResMetrics(settings: SettingsProvider) =
            HighResMetricsEnabled { settings.structuredLogSettings.highResMetricsEnabled }

        @Provides
        fun dailyHeartbeatEnabled(settings: SettingsProvider) =
            DailyHeartbeatEnabled { settings.metricsSettings.dailyHeartbeatEnabled }

        @Provides
        fun logcatCollectionInterval(settings: SettingsProvider) =
            LogcatCollectionInterval { settings.logcatSettings.collectionInterval }

        @Provides
        fun metricsCollectionInterval(settings: SettingsProvider) =
            MetricsCollectionInterval { settings.metricsSettings.collectionInterval }

        @Provides
        fun constraints(settings: SettingsProvider) = UploadConstraints { settings.httpApiSettings.uploadConstraints }

        @Provides
        fun metricReportEnabled(settings: SettingsProvider) =
            MetricReportEnabled { settings.structuredLogSettings.metricsReportEnabled }

        @Provides
        fun projectKey(settings: SettingsProvider) = ProjectKey { settings.httpApiSettings.projectKey }

        @Provides
        fun timeoutConfig(settings: SettingsProvider) = TimeoutConfig { settings.packageManagerSettings.commandTimeout }

        @Provides
        fun bugreportSettings(settings: SettingsProvider) = settings.bugReportSettings

        @Provides
        fun logcatSettings(settings: SettingsProvider) = settings.logcatSettings

        @Provides
        fun metricSettings(settings: SettingsProvider) = settings.metricsSettings

        @Provides
        fun batteryStatsSettings(settings: SettingsProvider) = settings.batteryStatsSettings

        @Provides
        fun dropboxSettings(settings: SettingsProvider) = settings.dropBoxSettings

        @Provides
        fun holdingArea(settings: SettingsProvider) = settings.fileUploadHoldingAreaSettings

        @Provides
        fun deviceInfoSettings(settingsProvider: SettingsProvider) = settingsProvider.deviceInfoSettings

        @Provides
        fun httpApiSettings(settingsProvider: SettingsProvider) = settingsProvider.httpApiSettings

        @Provides
        fun structuredLogSettings(settingsProvider: SettingsProvider) = settingsProvider.structuredLogSettings

        @Provides
        fun fleetSamplingSettings(settingsProvider: SettingsProvider) = settingsProvider.fleetSamplingSettings

        @Provides
        fun chroniclerSettings(settingsProvider: SettingsProvider) = settingsProvider.chroniclerSettings

        @Provides
        fun significantAppsSettings(settingsProvider: SettingsProvider) = settingsProvider.significantAppsSettings

        @Provides
        fun storageSettings(settingsProvider: SettingsProvider) = settingsProvider.storageSettings

        @Provides
        fun networkUsageSettings(settingsProvider: SettingsProvider) = settingsProvider.networkUsageSettings

        @Provides
        fun maxMarStorage(settings: SettingsProvider) =
            MaxMarStorageBytes { settings.httpApiSettings.maxMarStorageBytes }

        @Provides
        fun zipCompressionLevel(settings: SettingsProvider) =
            ZipCompressionLevel { settings.httpApiSettings.zipCompressionLevel }

        @Provides
        fun maxMarUnsampledAge(settings: SettingsProvider) =
            MarUnsampledMaxStorageAge { settings.httpApiSettings.maxMarUnsampledStoredAge }

        @Provides
        fun maxMarUnsampledStorageBytes(settings: SettingsProvider) =
            MarUnsampledMaxStorageBytes { settings.httpApiSettings.maxMarUnsampledStoredBytes }

        @Provides
        fun scrubTombstones(settings: SettingsProvider) =
            DropboxScrubTombstones { settings.dropBoxSettings.scrubTombstones }

        @Provides
        fun cachePackageManagerReport(settings: SettingsProvider) =
            CachePackageManagerReport { settings.metricsSettings.cachePackageManagerReport }

        @Provides
        fun dropBoxForceEnableWtfTags(settings: SettingsProvider) =
            DropBoxForceEnableWtfTags { settings.dropBoxSettings.forceEnableWtfTags }

        @Provides
        fun operationalCrashesExclusions(settings: SettingsProvider): OperationalCrashesExclusions =
            OperationalCrashesExclusions { settings.metricsSettings.operationalCrashesExclusions }

        @Provides
        fun metricsPollingInterval(settingsFlow: SettingsFlow) =
            MetricsPollingIntervalFlow { settingsFlow.settings.map { it.metricsSettings.pollingInterval } }

        @Provides
        fun logs2Metrics(settings: SettingsProvider) =
            Logs2MetricsRules {
                Logs2MetricsRule.fromJson(settings.logcatSettings.logs2metricsConfig)
            }
    }
}
