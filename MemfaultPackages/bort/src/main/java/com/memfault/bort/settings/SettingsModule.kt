package com.memfault.bort.settings

import androidx.work.Constraints
import com.memfault.bort.AndroidAppIdScrubbingRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Duration

fun interface MetricReportEnabled : () -> Boolean
fun interface StructuredLogEnabled : () -> Boolean
fun interface HighResMetricsEnabled : () -> Boolean
fun interface UploadCompressionEnabled : () -> Boolean
fun interface BatchMarUploads : () -> Boolean
fun interface UseMarUpload : suspend () -> Boolean
fun interface UseDeviceConfig : suspend () -> Boolean
fun interface ProjectKey : () -> String
fun interface TimeoutConfig : () -> Duration
fun interface RulesConfig : () -> List<AndroidAppIdScrubbingRule>
fun interface UploadConstraints : () -> Constraints
fun interface LogcatCollectionInterval : () -> Duration
fun interface GetLogcatCollectionMode : () -> LogcatCollectionMode
fun interface MaxUploadAttempts : () -> Int
fun interface MaxMarStorageBytes : () -> Long
fun interface ZipCompressionLevel : () -> Int
fun interface MarUnsampledMaxStorageAge : () -> Duration
fun interface MarUnsampledMaxStorageBytes : () -> Long
fun interface DropboxScrubTombstones : () -> Boolean

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    companion object {
        @Provides
        fun rulesConfig(settings: SettingsProvider) =
            RulesConfig { settings.dataScrubbingSettings.rules.filterIsInstance(AndroidAppIdScrubbingRule::class.java) }

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
        fun useMarUpload(settings: SettingsProvider) =
            UseMarUpload { settings.httpApiSettings.useMarUpload() }

        @Provides
        fun useDeviceConfig(settings: SettingsProvider) =
            UseDeviceConfig { settings.httpApiSettings.useDeviceConfig() }

        @Provides
        fun structuredLog(settings: SettingsProvider) =
            StructuredLogEnabled { settings.structuredLogSettings.dataSourceEnabled }

        @Provides
        fun highResMetrics(settings: SettingsProvider) =
            HighResMetricsEnabled { settings.structuredLogSettings.highResMetricsEnabled }

        @Provides
        fun logcatCollectionInterval(settings: SettingsProvider) =
            LogcatCollectionInterval { settings.logcatSettings.collectionInterval }

        @Provides
        fun logcatCollectionMode(settings: SettingsProvider) =
            GetLogcatCollectionMode { settings.logcatSettings.collectionMode }

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
        fun storageSettings(settingsProvider: SettingsProvider) = settingsProvider.storageSettings

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
    }
}
