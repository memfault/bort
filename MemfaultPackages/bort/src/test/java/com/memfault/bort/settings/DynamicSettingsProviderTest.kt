package com.memfault.bort.settings

import androidx.work.NetworkType
import androidx.work.NetworkType.CONNECTED
import androidx.work.NetworkType.UNMETERED
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.AndroidAppIdScrubbingRule
import com.memfault.bort.BortJson
import com.memfault.bort.CredentialScrubbingRule
import com.memfault.bort.DumpsterCapabilities
import com.memfault.bort.EmailScrubbingRule
import com.memfault.bort.UnknownScrubbingRule
import com.memfault.bort.settings.LogcatCollectionMode.CONTINUOUS
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.time.boxed
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DynamicSettingsProviderTest {
    private val projectKeyProvider = mockk<ProjectKeyProvider>(relaxed = true)

    @Test
    fun testParseRemoteSettings() {
        assertThat(SETTINGS_FIXTURE.toSettings()).isEqualTo(EXPECTED_SETTINGS)
    }

    /**
     * Checks that default values are applied for new settings, in the case that the server does not provide a value
     * (e.g. if backend changes were not deployed yet, or have to be rolled back).
     *
     * If this test fails, you need to add default values in [FetchedSettings].
     *
     * Do NOT add new fields to [SETTINGS_FIXTURE_WITH_MISSING_FIELDS] to fix failures here.
     */
    @Test
    fun testParseRemoteSettingsWithMissingFields() {
        assertThat(SETTINGS_FIXTURE_WITH_MISSING_FIELDS.toSettings()).isEqualTo(EXPECTED_SETTINGS_DEFAULT)
    }

    @Test
    fun testParseInvalidSettingsReturnsSerializationException() {
        assertThrows(SerializationException::class.java) { FetchedSettings.from("trash") { BortJson } }
    }

    companion object {
        private const val MODEL_KEY_OLD = "model.key.old"
        private const val MODEL_KEY_NEW = "model.key.new"
    }

    @Test
    fun testInvalidation() {
        val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider = mockk {
            every { get() } returns
                SETTINGS_FIXTURE.toSettings()
                    .copy(
                        bortMinLogcatLevel = LogLevel.VERBOSE.level,
                        deviceInfoAndroidHardwareVersionKey = MODEL_KEY_OLD,
                        httpApiZipCompressionLevel = 1,
                    ) andThen
                SETTINGS_FIXTURE.toSettings()
                    .copy(
                        bortMinLogcatLevel = LogLevel.INFO.level,
                        deviceInfoAndroidHardwareVersionKey = MODEL_KEY_NEW,
                        httpApiZipCompressionLevel = 2,
                    )
        }
        val dumpsterCapabilities = mockk<DumpsterCapabilities> {
            every { supportsContinuousLogging() } answers { true }
        }

        val settings = DynamicSettingsProvider(
            storedSettingsPreferenceProvider = storedSettingsPreferenceProvider,
            dumpsterCapabilities = dumpsterCapabilities,
            projectKeyProvider = projectKeyProvider,
            cachedClientServerMode = mockk(),
            devMode = mockk(),
        )

        // The first call will use the value in resources
        assertThat(settings.minLogcatLevel).isEqualTo(LogLevel.VERBOSE)

        // The second call will still operate on a cached settings value
        assertThat(settings.minLogcatLevel).isEqualTo(LogLevel.VERBOSE)
        assertThat(settings.deviceInfoSettings.androidHardwareVersionKey).isEqualTo(MODEL_KEY_OLD)
        assertThat(settings.httpApiSettings.zipCompressionLevel).isEqualTo(1)

        // This will cause settings to be reconstructed on the next call, which will
        // read the new min_log_level from shared preferences
        settings.invalidate()
        assertThat(settings.minLogcatLevel).isEqualTo(LogLevel.INFO)
        assertThat(settings.deviceInfoSettings.androidHardwareVersionKey).isEqualTo(MODEL_KEY_NEW)
        assertThat(settings.httpApiSettings.zipCompressionLevel).isEqualTo(2)
    }

    @Test
    fun dumpsterSupportsContinuousLogging() {
        val dumpsterCapabilities = mockk<DumpsterCapabilities> {
            every { supportsContinuousLogging() } answers { true }
        }
        val prefProvider = object : ReadonlyFetchedSettingsProvider {
            override fun get(): FetchedSettings = SETTINGS_FIXTURE.toSettings()
        }
        val provider = DynamicSettingsProvider(prefProvider, dumpsterCapabilities, mockk(), mockk(), projectKeyProvider)
        assertThat(provider.logcatSettings.collectionMode).isEqualTo(CONTINUOUS)
    }

    @Test
    fun dumpsterDoesNotSupportContinuousLogging() {
        val dumpsterCapabilities = mockk<DumpsterCapabilities> {
            every { supportsContinuousLogging() } answers { false }
        }
        val prefProvider = object : ReadonlyFetchedSettingsProvider {
            override fun get(): FetchedSettings = SETTINGS_FIXTURE.toSettings()
        }

        val provider = DynamicSettingsProvider(prefProvider, dumpsterCapabilities, mockk(), mockk(), projectKeyProvider)

        assertThat(provider.logcatSettings.collectionMode).isEqualTo(PERIODIC)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `otaDownloadNetworkConstraint default UNSET`(allowMeteredConnection: Boolean) {
        val defaultSettings = SETTINGS_FIXTURE.toSettings()
            .copy(otaDownloadNetworkConstraintAllowMeteredConnection = allowMeteredConnection)

        val prefProvider = object : ReadonlyFetchedSettingsProvider {
            override fun get(): FetchedSettings = defaultSettings
        }

        val provider = DynamicSettingsProvider(prefProvider, mockk(), mockk(), mockk(), projectKeyProvider)

        assertThat(provider.otaSettings.downloadNetworkConstraint)
            .isEqualTo(if (allowMeteredConnection) CONNECTED else UNMETERED)
    }

    @ParameterizedTest
    @ValueSource(strings = ["NOT_ROAMING", "CONNECTED", "UNMETERED"])
    fun `otaDownloadNetworkConstraint override NetworkType`(networkType: String) {
        val defaultSettings = SETTINGS_FIXTURE.toSettings()
            .copy(otaDownloadNetworkConstraint = networkType)

        val prefProvider = object : ReadonlyFetchedSettingsProvider {
            override fun get(): FetchedSettings = defaultSettings
        }

        val provider = DynamicSettingsProvider(prefProvider, mockk(), mockk(), mockk(), projectKeyProvider)

        assertThat(provider.otaSettings.downloadNetworkConstraint)
            .isEqualTo(NetworkType.valueOf(networkType))
    }
}

internal val EXPECTED_SETTINGS_DEFAULT = FetchedSettings(
    batteryStatsCommandTimeout = 60.seconds.boxed(),
    batteryStatsDataSourceEnabled = false,
    bortMinLogcatLevel = 5,
    bortMinStructuredLogLevel = 3,
    bortSettingsUpdateInterval = 1234.milliseconds.boxed(),
    bortEventLogEnabled = true,
    bortInternalLogToDiskEnabled = false,
    bugReportCollectionInterval = 1234.milliseconds.boxed(),
    bugReportDataSourceEnabled = false,
    bugReportFirstBugReportDelayAfterBoot = 5678.milliseconds.boxed(),
    bugReportRequestRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 3,
        defaultPeriod = 30.minutes.boxed(),
        maxBuckets = 1,
    ),
    bugReportMaxStorageBytes = 50000000,
    bugReportMaxStoredAge = 0.days.boxed(),
    bugReportMaxUploadAttempts = 15,
    bugReportOptionsMinimal = true,
    bugReportPeriodicRateLimitingPercentOfPeriod = 50,
    dataScrubbingRules = listOf(
        AndroidAppIdScrubbingRule("com.memfault.*"),
        AndroidAppIdScrubbingRule("com.yolo.*"),
        EmailScrubbingRule,
        CredentialScrubbingRule,
        UnknownScrubbingRule,
    ),
    deviceInfoAndroidBuildVersionKey = "ro.build.another",
    deviceInfoAndroidBuildVersionSource = "build_fingerprint",
    deviceInfoAndroidDeviceSerialKey = "ro.another.serial",
    deviceInfoAndroidHardwareVersionKey = "ro.product.hw",
    dropBoxAnrsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    ),
    dropBoxExcludedTags = setOf("TAG1", "TAG2"),
    dropBoxDataSourceEnabled = true,
    dropBoxJavaExceptionsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 4,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 100,
    ),
    dropBoxKmsgsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    ),
    dropBoxStructuredLogRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    ),
    dropBoxTombstonesRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    ),
    fileUploadHoldingAreaMaxStoredEventsOfInterest = 50,
    fileUploadHoldingAreaTrailingMargin = 300000.milliseconds.boxed(),
    httpApiDeviceBaseUrl = "https://device2.memfault.com",
    httpApiFilesBaseUrl = "https://files2.memfault.com",
    httpApiUploadCompressionEnabled = false,
    httpApiUploadNetworkConstraintAllowMeteredConnection = false,
    httpApiConnectTimeout = 30.seconds.boxed(),
    httpApiWriteTimeout = 0.seconds.boxed(),
    httpApiReadTimeout = 0.seconds.boxed(),
    httpApiCallTimeout = 0.seconds.boxed(),
    httpApiUseMarUpload = true,
    httpApiUseDeviceConfig = true,
    httpApiBatchMarUploads = true,
    httpApiBatchedMarUploadPeriod = 2.hours.boxed(),
    logcatCommandTimeout = 60.seconds.boxed(),
    logcatCollectionInterval = 9999.milliseconds.boxed(),
    logcatDataSourceEnabled = false,
    logcatFilterSpecs = listOf(
        LogcatFilterSpec(priority = LogcatPriority.WARN, tag = "*"),
        LogcatFilterSpec(priority = LogcatPriority.VERBOSE, tag = "bort"),
    ),
    logcatKernelOopsDataSourceEnabled = true,
    logcatKernelOopsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 3,
        defaultPeriod = 6.hours.boxed(),
        maxBuckets = 1,
    ),
    logcatCollectionMode = PERIODIC,
    logcatContinuousDumpThresholdBytes = 25 * 1024 * 1024,
    logcatContinuousDumpThresholdTime = 15.minutes.boxed(),
    metricsCollectionInterval = 91011.milliseconds.boxed(),
    metricsDataSourceEnabled = false,
    metricsSystemProperties = listOf("ro.build.type", "persist.sys.timezone"),
    metricsAppVersions = listOf(),
    metricsMaxNumAppVersions = 50,
    metricsReporterCollectionInterval = 10.minutes.boxed(),
    otaUpdateCheckInterval = 12.hours.boxed(),
    packageManagerCommandTimeout = 60.seconds.boxed(),
    rebootEventsDataSourceEnabled = true,
    rebootEventsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 5,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    ),
    selinuxViolationEventsDataSourceEnabled = false,
    selinuxViolationEventsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 2,
        defaultPeriod = 24.hours.boxed(),
        maxBuckets = 15,
    ),
    structuredLogDataSourceEnabled = true,
    structuredLogDumpPeriod = 60.minutes.boxed(),
    structuredLogMaxMessageSizeBytes = 4096,
    structuredLogMinStorageThresholdBytes = 268435456,
    structuredLogNumEventsBeforeDump = 1500,
    structuredLogRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 1000,
        defaultPeriod = 15.minutes.boxed(),
        maxBuckets = 1,
    ),
    metricReportRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 2,
        defaultPeriod = 1.hours.boxed(),
        maxBuckets = 1,
    ),
    metricReportEnabled = true,
    metricsCachePackages = true,
)

private val EXPECTED_SETTINGS = EXPECTED_SETTINGS_DEFAULT.copy(
    storageMaxClientServerFileTransferStorageBytes = 55000000,
    httpApiDeviceConfigInterval = 1.hours.boxed(),
    httpApiZipCompressionLevel = 5,
    httpApiMarUnsampledMaxStoredAge = 5.days.boxed(),
    logcatCollectionMode = CONTINUOUS,
    storageUsageReporterTempMaxStorageBytes = 10000001,
    storageBortTempMaxStorageBytes = 250000001,
    batteryStatsCollectSummary = true,
)

internal val SETTINGS_FIXTURE = """
            {
              "data": {
                "battery_stats.collect_summary": true,
                "battery_stats.data_source_enabled": false,
                "battery_stats.command_timeout_ms" : 60000,
                "bort.min_log_level": 5,
                "bort.min_structured_log_level": 3,
                "bort.event_log_enabled": true,
                "bort.internal_log_to_disk_enabled": false,
                "bort.settings_update_interval_ms": 1234,
                "bug_report.collection_interval_ms": 1234,
                "bug_report.data_source_enabled": false,
                "bug_report.first_bug_report_delay_after_boot_ms": 5678,
                "bug_report.max_storage_bytes" : 50000000,
                "bug_report.max_stored_age_ms" : 0,
                "bug_report.max_upload_attempts": 15,
                "bug_report.options.minimal": true,
                "bug_report.periodic_rate_limiting_percent": 50,
                "bug_report.request_rate_limiting_settings": {
                    "default_capacity": 3,
                    "default_period_ms": 1800000,
                    "max_buckets": 1
                },
                "data_scrubbing.rules": [
                    {"app_id_pattern": "com.memfault.*", "type": "android_app_id"},
                    {"app_id_pattern": "com.yolo.*", "type": "android_app_id"},
                    {"type": "text_email"},
                    {"type": "text_credential"},
                    {"type": "text_addresses_unknown"}
                ],
                "device_info.android_build_version_key": "ro.build.another",
                "device_info.android_build_version_source": "build_fingerprint",
                "device_info.android_device_serial_key": "ro.another.serial",
                "device_info.android_hardware_version_key": "ro.product.hw",
                "drop_box.anrs.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "drop_box.data_source_enabled": true,
                "drop_box.excluded_tags": ["TAG1", "TAG2"],
                "drop_box.scrub_tombstones": false,
                "drop_box.java_exceptions.rate_limiting_settings": {
                    "default_capacity": 4,
                    "default_period_ms": 900000,
                    "max_buckets": 100
                },
                "drop_box.wtfs.rate_limiting_settings": {
                    "default_capacity": 5,
                    "default_period_ms": 86400000,
                    "max_buckets": 10
                },
                "drop_box.wtfs_total.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 86400000,
                    "max_buckets": 1
                },
                "drop_box.kmsgs.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "drop_box.structured_log.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "drop_box.tombstones.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "file_upload_holding_area.max_stored_events_of_interest": 50,
                "file_upload_holding_area.trailing_margin_ms": 300000,
                "http_api.device_base_url": "https://device2.memfault.com",
                "http_api.files_base_url": "https://files2.memfault.com",
                "http_api.upload_compression_enabled": False,
                "http_api.upload_network_constraint_allow_metered_connection": False,
                "http_api.connect_timeout_ms": 30000,
                "http_api.write_timeout_ms": 0,
                "http_api.read_timeout_ms": 0,
                "http_api.call_timeout_ms": 0,
                "http_api.zip_compression_level": 5,
                "http_api.use_mar_upload": True,
                "http_api.use_device_config": True,
                "http_api.device_config_interval_ms": 3600000,
                "http_api.batched_mar_upload_period_ms": 7200000,
                "http_api.max_mar_file_size_bytes": 250000000,
                "http_api.max_mar_file_storage_bytes": 250000000,
                "http_api.mar_unsampled_max_stored_age_ms": 432000000,
                "logcat.collection_interval_ms": 9999,
                "logcat.command_timeout_ms" : 60000,
                "logcat.data_source_enabled": False,
                "logcat.filter_specs": [{"priority": "W", "tag": "*"}, {"priority": "V", "tag": "bort"}],
                "logcat.kernel_oops.data_source_enabled": true,
                "logcat.kernel_oops.rate_limiting_settings": {
                    "default_capacity": 3,
                    "default_period_ms": 21600000,
                    "max_buckets": 1
                },
                "logcat.store_unsampled": false,
                "logcat.collection_mode": "continuous",
                "logcat.continuous_dump_threshold_bytes": 26214400,
                "logcat.continuous_dump_threshold_time_ms": 900000,
                "logcat.continuous_dump_wrapping_timeout_ms": 900000,
                "metrics.collection_interval_ms": 91011,
                "metrics.data_source_enabled": false,
                "metrics.system_properties": ["ro.build.type", "persist.sys.timezone"],
                "metrics.app_versions": [],
                "metrics.max_num_app_versions": 50,
                "metrics.reporter_collection_interval_ms": 600000,
                "ota.update_check_interval_ms": 43200000,
                "package_manager.command_timeout_ms" : 60000,
                "reboot_events.data_source_enabled": true,
                "reboot_events.rate_limiting_settings": {
                    "default_capacity": 5,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "selinux_violation_events.data_source_enabled": false,
                "selinux_violation_events.rate_limiting_settings": {
                    "default_capacity": 2,
                    "default_period_ms": 86400000,
                    "max_buckets": 15
                },
                "structured_log.data_source_enabled": true,
                "structured_log.dump_period_ms": 3600000,
                "structured_log.max_message_size_bytes": 4096,
                "structured_log.min_storage_threshold_bytes": 268435456,
                "structured_log.num_events_before_dump": 1500,
                "structured_log.rate_limiting_settings": {
                    "default_capacity": 1000,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "storage.max_client_server_file_transfer_storage_bytes": 55000000,
                "storage.usage_reporter_temp_max_storage_bytes": 10000001,
                "storage.usage_reporter_temp_max_storage_age_ms": 86400000,
                "storage.bort_temp_max_storage_bytes": 250000001,
                "storage.bort_temp_max_storage_age_ms": 604800000
              }
            }
""".trimIndent()

/**
 * Represents settings json at the point in time where we added fallback values to settings deserialization.
 *
 * Do not (ever) add new fields to this json (changing existing values is OK).
 */
internal val SETTINGS_FIXTURE_WITH_MISSING_FIELDS = """
            {
              "data": {
                "battery_stats.data_source_enabled": false,
                "battery_stats.command_timeout_ms" : 60000,
                "bort.min_log_level": 5,
                "bort.event_log_enabled": true,
                "bort.settings_update_interval_ms": 1234,
                "bug_report.collection_interval_ms": 1234,
                "bug_report.data_source_enabled": false,
                "bug_report.first_bug_report_delay_after_boot_ms": 5678,
                "bug_report.max_upload_attempts": 15,
                "bug_report.options.minimal": true,
                "bug_report.request_rate_limiting_settings": {
                    "default_capacity": 3,
                    "default_period_ms": 1800000,
                    "max_buckets": 1
                },
                "data_scrubbing.rules": [
                    {"app_id_pattern": "com.memfault.*", "type": "android_app_id"},
                    {"app_id_pattern": "com.yolo.*", "type": "android_app_id"},
                    {"type": "text_email"},
                    {"type": "text_credential"},
                    {"type": "text_addresses_unknown"}
                ],
                "device_info.android_build_version_key": "ro.build.another",
                "device_info.android_build_version_source": "build_fingerprint",
                "device_info.android_device_serial_key": "ro.another.serial",
                "device_info.android_hardware_version_key": "ro.product.hw",
                "drop_box.anrs.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "drop_box.data_source_enabled": true,
                "drop_box.excluded_tags": ["TAG1", "TAG2"],
                "drop_box.java_exceptions.rate_limiting_settings": {
                    "default_capacity": 4,
                    "default_period_ms": 900000,
                    "max_buckets": 100
                },
                "drop_box.kmsgs.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "drop_box.structured_log.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "drop_box.tombstones.rate_limiting_settings": {
                    "default_capacity": 10,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "file_upload_holding_area.max_stored_events_of_interest": 50,
                "file_upload_holding_area.trailing_margin_ms": 300000,
                "http_api.device_base_url": "https://device2.memfault.com",
                "http_api.files_base_url": "https://files2.memfault.com",
                "http_api.upload_compression_enabled": False,
                "http_api.upload_network_constraint_allow_metered_connection": False,
                "http_api.connect_timeout_ms": 30000,
                "http_api.write_timeout_ms": 0,
                "http_api.read_timeout_ms": 0,
                "http_api.call_timeout_ms": 0,
                "logcat.collection_interval_ms": 9999,
                "logcat.command_timeout_ms" : 60000,
                "logcat.data_source_enabled": False,
                "logcat.filter_specs": [{"priority": "W", "tag": "*"}, {"priority": "V", "tag": "bort"}],
                "metrics.collection_interval_ms": 91011,
                "metrics.data_source_enabled": false,
                "package_manager.command_timeout_ms" : 60000,
                "reboot_events.data_source_enabled": true,
                "reboot_events.rate_limiting_settings": {
                    "default_capacity": 5,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                },
                "selinux_violation_events.data_source_enabled": false,
                "selinux_violation_events.rate_limiting_settings": {
                    "default_capacity": 2,
                    "default_period_ms": 86400000,
                    "max_buckets": 15
                },
                "structured_log.data_source_enabled": true,
                "structured_log.dump_period_ms": 3600000,
                "structured_log.max_message_size_bytes": 4096,
                "structured_log.num_events_before_dump": 1500,
                "structured_log.rate_limiting_settings": {
                    "default_capacity": 1000,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                }
              }
            }
""".trimIndent()

fun String.toSettings() =
    BortJson.decodeFromString(FetchedSettings.FetchedSettingsContainer.serializer(), this).data
