package com.memfault.bort.settings

import com.memfault.bort.DataScrubbingRule
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.DurationAsMillisecondsLong
import com.memfault.bort.time.boxed
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FetchedSettings(
    @SerialName("battery_stats.command_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val batteryStatsCommandTimeout: BoxedDuration,

    @SerialName("battery_stats.data_source_enabled")
    val batteryStatsDataSourceEnabled: Boolean,

    @SerialName("battery_stats.use_hrt")
    val batteryStatsUseHrt: Boolean = false,

    @SerialName("bort.min_log_level")
    val bortMinLogcatLevel: Int,

    @SerialName("bort.min_structured_log_level")
    val bortMinStructuredLogLevel: Int = LogLevel.INFO.level,

    @SerialName("bort.event_log_enabled")
    val bortEventLogEnabled: Boolean,

    @SerialName("bort.internal_log_to_disk_enabled")
    val bortInternalLogToDiskEnabled: Boolean = false,

    @SerialName("bort.settings_update_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val bortSettingsUpdateInterval: BoxedDuration,

    @SerialName("bug_report.collection_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val bugReportCollectionInterval: BoxedDuration,

    @SerialName("bug_report.data_source_enabled")
    val bugReportDataSourceEnabled: Boolean,

    @SerialName("bug_report.first_bug_report_delay_after_boot_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val bugReportFirstBugReportDelayAfterBoot: BoxedDuration,

    @SerialName("bug_report.request_rate_limiting_settings")
    val bugReportRequestRateLimitingSettings: RateLimitingSettings,

    @SerialName("bug_report.max_storage_bytes")
    val bugReportMaxStorageBytes: Int = 50000000,

    @SerialName("bug_report.max_stored_age_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val bugReportMaxStoredAge: BoxedDuration = ZERO.boxed(),

    @SerialName("bug_report.max_upload_attempts")
    val bugReportMaxUploadAttempts: Int,

    @SerialName("bug_report.options.minimal")
    val bugReportOptionsMinimal: Boolean,

    @SerialName("bug_report.periodic_rate_limiting_percent")
    val bugReportPeriodicRateLimitingPercentOfPeriod: Int = 50,

    @SerialName("data_scrubbing.rules")
    val dataScrubbingRules: List<DataScrubbingRule>,

    @SerialName("device_info.android_build_version_key")
    val deviceInfoAndroidBuildVersionKey: String,

    @SerialName("device_info.android_build_version_source")
    val deviceInfoAndroidBuildVersionSource: String,

    @SerialName("device_info.android_device_serial_key")
    val deviceInfoAndroidDeviceSerialKey: String,

    @SerialName("device_info.android_hardware_version_key")
    val deviceInfoAndroidHardwareVersionKey: String,

    @SerialName("drop_box.excluded_tags")
    val dropBoxExcludedTags: Set<String>,

    @SerialName("drop_box.anrs.rate_limiting_settings")
    val dropBoxAnrsRateLimitingSettings: RateLimitingSettings,

    @SerialName("drop_box.data_source_enabled")
    val dropBoxDataSourceEnabled: Boolean,

    @SerialName("drop_box.scrub_tombstones")
    val dropBoxScrubTombstones: Boolean = false,

    @SerialName("drop_box.java_exceptions.rate_limiting_settings")
    val dropBoxJavaExceptionsRateLimitingSettings: RateLimitingSettings,

    @SerialName("drop_box.wtfs.rate_limiting_settings")
    val dropBoxWtfsRateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 5,
        defaultPeriod = 24.hours.boxed(),
        maxBuckets = 10,
    ),

    @SerialName("drop_box.wtfs_total.rate_limiting_settings")
    val dropBoxWtfsTotalRateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 24.hours.boxed(),
        maxBuckets = 1,
    ),

    @SerialName("drop_box.kmsgs.rate_limiting_settings")
    val dropBoxKmsgsRateLimitingSettings: RateLimitingSettings,

    @SerialName("drop_box.structured_log.rate_limiting_settings")
    val dropBoxStructuredLogRateLimitingSettings: RateLimitingSettings,

    @SerialName("drop_box.tombstones.rate_limiting_settings")
    val dropBoxTombstonesRateLimitingSettings: RateLimitingSettings,

    @SerialName("drop_box.continuous_logfile.rate_limiting_settings")
    val dropBoxContinuousLogFileLimitingSettings: RateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 4,
        defaultPeriod = 60.minutes.boxed(),
        maxBuckets = 1,
    ),

    @SerialName("file_upload_holding_area.max_stored_events_of_interest")
    val fileUploadHoldingAreaMaxStoredEventsOfInterest: Int,

    @SerialName("file_upload_holding_area.trailing_margin_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val fileUploadHoldingAreaTrailingMargin: BoxedDuration,

    @SerialName("http_api.device_base_url")
    val httpApiDeviceBaseUrl: String,

    @SerialName("http_api.files_base_url")
    val httpApiFilesBaseUrl: String,

    @SerialName("http_api.ingress_base_url")
    val httpApiIngressBaseUrl: String,

    @SerialName("http_api.upload_compression_enabled")
    val httpApiUploadCompressionEnabled: Boolean,

    @SerialName("http_api.upload_network_constraint_allow_metered_connection")
    val httpApiUploadNetworkConstraintAllowMeteredConnection: Boolean,

    @SerialName("http_api.connect_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiConnectTimeout: BoxedDuration,

    @SerialName("http_api.write_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiWriteTimeout: BoxedDuration,

    @SerialName("http_api.read_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiReadTimeout: BoxedDuration,

    @SerialName("http_api.call_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiCallTimeout: BoxedDuration,

    @SerialName("http_api.zip_compression_level")
    val httpApiZipCompressionLevel: Int = 4,

    @SerialName("http_api.use_mar_upload")
    val httpApiUseMarUpload: Boolean = true,

    @SerialName("http_api.use_device_config")
    val httpApiUseDeviceConfig: Boolean = true,

    @SerialName("http_api.device_config_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiDeviceConfigInterval: BoxedDuration = 2.hours.boxed(),

    @SerialName("http_api.batch_mar_uploads")
    val httpApiBatchMarUploads: Boolean = true,

    @SerialName("http_api.batched_mar_upload_period_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiBatchedMarUploadPeriod: BoxedDuration = 2.hours.boxed(),

    @SerialName("http_api.max_mar_file_size_bytes")
    val httpApiMaxMarFileSizeBytes: Int = 250_000_000,

    @SerialName("http_api.max_mar_file_storage_bytes")
    val httpApiMaxMarStorageBytes: Long = 250_000_000,

    @SerialName("http_api.mar_unsampled_max_stored_bytes")
    val httpApiMaxMarUnsampledStorageBytes: Long = 250_000_000,

    @SerialName("http_api.mar_unsampled_max_stored_age_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val httpApiMarUnsampledMaxStoredAge: BoxedDuration = 7.days.boxed(),

    @SerialName("logcat.collection_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val logcatCollectionInterval: BoxedDuration,

    @SerialName("logcat.command_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val logcatCommandTimeout: BoxedDuration,

    @SerialName("logcat.data_source_enabled")
    val logcatDataSourceEnabled: Boolean,

    @SerialName("logcat.filter_specs")
    val logcatFilterSpecs: List<LogcatFilterSpec>,

    @SerialName("logcat.kernel_oops.data_source_enabled")
    val logcatKernelOopsDataSourceEnabled: Boolean = true,

    @SerialName("logcat.kernel_oops.rate_limiting_settings")
    val logcatKernelOopsRateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 3,
        defaultPeriod = 6.hours.boxed(),
        maxBuckets = 1,
    ),

    @SerialName("logcat.store_unsampled")
    val logcatStoreUnsampled: Boolean = false,

    @SerialName("logcat.collection_mode")
    val logcatCollectionMode: LogcatCollectionMode = LogcatCollectionMode.PERIODIC,

    @SerialName("logcat.continuous_dump_threshold_bytes")
    val logcatContinuousDumpThresholdBytes: Int = 25 * 1024 * 1024,

    @SerialName("logcat.continuous_dump_threshold_time_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val logcatContinuousDumpThresholdTime: BoxedDuration = 15.minutes.boxed(),

    @SerialName("logcat.continuous_dump_wrapping_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val logcatContinuousDumpWrappingTimeout: BoxedDuration = 15.minutes.boxed(),

    @SerialName("metrics.collection_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val metricsCollectionInterval: BoxedDuration,

    @SerialName("metrics.data_source_enabled")
    val metricsDataSourceEnabled: Boolean,

    @SerialName("metrics.system_properties")
    val metricsSystemProperties: List<String> = listOf("ro.build.type"),

    @SerialName("metrics.app_versions")
    val metricsAppVersions: List<String> = listOf(),

    @SerialName("metrics.max_num_app_versions")
    val metricsMaxNumAppVersions: Int = 50,

    @SerialName("metrics.reporter_collection_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val metricsReporterCollectionInterval: BoxedDuration = 10.minutes.boxed(),

    @SerialName("ota.update_check_interval_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val otaUpdateCheckInterval: BoxedDuration = 12.hours.boxed(),

    @SerialName("ota.download_network_constraint_allow_metered_connection")
    val otaDownloadNetworkConstraintAllowMeteredConnection: Boolean = false,

    @SerialName("package_manager.command_timeout_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val packageManagerCommandTimeout: BoxedDuration,

    @SerialName("reboot_events.data_source_enabled")
    val rebootEventsDataSourceEnabled: Boolean,

    @SerialName("reboot_events.rate_limiting_settings")
    val rebootEventsRateLimitingSettings: RateLimitingSettings,

    @SerialName("sampling.logging_active")
    val fleetSamplingLoggingActive: Boolean = false,

    @SerialName("sampling.debugging_active")
    val fleetSamplingDebuggingActive: Boolean = false,

    @SerialName("sampling.monitoring_active")
    val fleetSamplingMonitroringActive: Boolean = false,

    @SerialName("structured_log.data_source_enabled")
    val structuredLogDataSourceEnabled: Boolean,

    @SerialName("structured_log.dump_period_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val structuredLogDumpPeriod: BoxedDuration,

    @SerialName("structured_log.max_message_size_bytes")
    val structuredLogMaxMessageSizeBytes: Long,

    @SerialName("structured_log.min_storage_threshold_bytes")
    val structuredLogMinStorageThresholdBytes: Long = 268435456,

    @SerialName("structured_log.num_events_before_dump")
    val structuredLogNumEventsBeforeDump: Long,

    @SerialName("structured_log.rate_limiting_settings")
    val structuredLogRateLimitingSettings: RateLimitingSettings,

    @SerialName("metric_report.rate_limiting_settings")
    val metricReportRateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 2,
        defaultPeriod = 1.hours.boxed(),
        maxBuckets = 1,
    ),

    @SerialName("mar_file.rate_limiting_settings")
    val marFileRateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
        // TODO This is intentionally high, initially: all files/events will be separate mar uploads until we implement
        // bundled mar files - at which point this can be reduced to the real expected rate.
        defaultCapacity = 500,
        defaultPeriod = 1.hours.boxed(),
        maxBuckets = 1,
    ),

    @SerialName("metric_report.enabled")
    val metricReportEnabled: Boolean = true,

    @SerialName("metric_report.high_res_telemetry")
    val highResTelemetryEnabled: Boolean = true,

    @SerialName("storage.max_client_server_file_transfer_storage_bytes")
    val storageMaxClientServerFileTransferStorageBytes: Long = 50_000_000,

    @SerialName("storage.max_client_server_file_transfer_storage_age_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val storageMaxClientServerFileTransferStorageAge: BoxedDuration = 7.days.boxed(),

    @SerialName("storage.usage_reporter_temp_max_storage_bytes")
    val storageUsageReporterTempMaxStorageBytes: Long = 10_000_000,

    @SerialName("storage.usage_reporter_temp_max_storage_age_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val storageUsageReporterTempMaxStorageAge: BoxedDuration = 1.days.boxed(),

    @SerialName("storage.bort_temp_max_storage_bytes")
    val storageBortTempMaxStorageBytes: Long = 250_000_000,

    @SerialName("storage.bort_temp_max_storage_age_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val storageBortTempMaxStorageAge: BoxedDuration = 7.days.boxed(),
) {
    @Serializable
    data class FetchedSettingsContainer(
        val data: FetchedSettings
    )

    companion object {
        fun from(input: String, getJsonDeserializer: () -> Json): FetchedSettings =
            getJsonDeserializer().decodeFromString(FetchedSettingsContainer.serializer(), input).data
    }
}

@Serializable
data class RateLimitingSettings(
    @SerialName("default_capacity")
    val defaultCapacity: Int,

    @SerialName("default_period_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val defaultPeriod: BoxedDuration,

    @SerialName("max_buckets")
    val maxBuckets: Int,
)

data class FetchedSettingsUpdate(
    val old: FetchedSettings,
    val new: FetchedSettings,
)
