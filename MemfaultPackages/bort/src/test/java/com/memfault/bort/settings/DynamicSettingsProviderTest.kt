package com.memfault.bort.settings

import com.memfault.bort.AndroidAppIdScrubbingRule
import com.memfault.bort.BortJson
import com.memfault.bort.CredentialScrubbingRule
import com.memfault.bort.EmailScrubbingRule
import com.memfault.bort.UnknownScrubbingRule
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.time.boxed
import io.mockk.every
import io.mockk.mockk
import kotlin.time.days
import kotlin.time.hours
import kotlin.time.milliseconds
import kotlin.time.minutes
import kotlin.time.seconds
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DynamicSettingsProviderTest {
    @Test
    fun testParseRemoteSettings() {
        assertEquals(
            EXPECTED_SETTINGS,
            SETTINGS_FIXTURE.toSettings(),
        )
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
        assertEquals(
            EXPECTED_SETTINGS,
            SETTINGS_FIXTURE_WITH_MISSING_FIELDS.toSettings(),
        )
    }

    @Test
    fun testParseInvalidSettingsReturnsSerializationException() {
        assertThrows(SerializationException::class.java) { FetchedSettings.from("trash") { BortJson } }
    }

    @Test
    fun testInvalidation() {
        val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider = mockk {
            every { get() } returns
                SETTINGS_FIXTURE.toSettings().copy(bortMinLogcatLevel = LogLevel.VERBOSE.level) andThen
                SETTINGS_FIXTURE.toSettings().copy(bortMinLogcatLevel = LogLevel.INFO.level)
        }

        val settings = DynamicSettingsProvider(storedSettingsPreferenceProvider)

        // The first call will use the value in resources
        assertEquals(LogLevel.VERBOSE, settings.minLogcatLevel)

        // The second call will still operate on a cached settings value
        assertEquals(LogLevel.VERBOSE, settings.minLogcatLevel)

        // This will cause settings to be reconstructed on the next call, which will
        // read the new min_log_level from shared preferences
        settings.invalidate()
        assertEquals(LogLevel.INFO, settings.minLogcatLevel)
    }
}

internal val EXPECTED_SETTINGS = FetchedSettings(
    batteryStatsCommandTimeout = 60.seconds.boxed(),
    batteryStatsDataSourceEnabled = false,
    bortMinLogcatLevel = 5,
    bortMinStructuredLogLevel = 3,
    bortSettingsUpdateInterval = 1234.milliseconds.boxed(),
    bortEventLogEnabled = true,
    bugReportCollectionInterval = 1234.milliseconds.boxed(),
    bugReportDataSourceEnabled = false,
    bugReportFirstBugReportDelayAfterBoot = 5678.milliseconds.boxed(),
    bugReportRequestRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 3,
        defaultPeriod = 1800000.milliseconds.boxed(),
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
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 1,
    ),
    dropBoxExcludedTags = setOf("TAG1", "TAG2"),
    dropBoxDataSourceEnabled = true,
    dropBoxJavaExceptionsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 4,
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 100,
    ),
    dropBoxKmsgsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 1,
    ),
    dropBoxStructuredLogRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 1,
    ),
    dropBoxTombstonesRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 10,
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 1,
    ),
    fileUploadHoldingAreaMaxStoredEventsOfInterest = 50,
    fileUploadHoldingAreaTrailingMargin = 300000.milliseconds.boxed(),
    httpApiDeviceBaseUrl = "https://device2.memfault.com",
    httpApiFilesBaseUrl = "https://files2.memfault.com",
    httpApiIngressBaseUrl = "https://ingress2.memfault.com",
    httpApiUploadCompressionEnabled = false,
    httpApiUploadNetworkConstraintAllowMeteredConnection = false,
    httpApiConnectTimeout = 30.seconds.boxed(),
    httpApiWriteTimeout = 0.seconds.boxed(),
    httpApiReadTimeout = 0.seconds.boxed(),
    httpApiCallTimeout = 0.seconds.boxed(),
    logcatCommandTimeout = 60.seconds.boxed(),
    logcatCollectionInterval = 9999.milliseconds.boxed(),
    logcatDataSourceEnabled = false,
    logcatFilterSpecs = listOf(
        LogcatFilterSpec(priority = LogcatPriority.WARN, tag = "*"),
        LogcatFilterSpec(priority = LogcatPriority.VERBOSE, tag = "bort"),
    ),
    metricsCollectionInterval = 91011.milliseconds.boxed(),
    metricsDataSourceEnabled = false,
    otaUpdateCheckInterval = 12.hours.boxed(),
    packageManagerCommandTimeout = 60.seconds.boxed(),
    rebootEventsDataSourceEnabled = true,
    rebootEventsRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 5,
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 1,
    ),
    structuredLogDataSourceEnabled = true,
    structuredLogDumpPeriod = 60.minutes.boxed(),
    structuredLogMaxMessageSizeBytes = 4096,
    structuredLogMinStorageThresholdBytes = 268435456,
    structuredLogNumEventsBeforeDump = 1500,
    structuredLogRateLimitingSettings = RateLimitingSettings(
        defaultCapacity = 1000,
        defaultPeriod = 900000.milliseconds.boxed(),
        maxBuckets = 1,
    ),
)

internal val SETTINGS_FIXTURE = """
            {
              "data": {
                "battery_stats.data_source_enabled": false,
                "battery_stats.command_timeout_ms" : 60000,
                "bort.min_log_level": 5,
                "bort.min_structured_log_level": 3,
                "bort.event_log_enabled": true,
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
                "http_api.ingress_base_url": "https://ingress2.memfault.com",
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
                "ota.update_check_interval_ms": 43200000,
                "package_manager.command_timeout_ms" : 60000,
                "reboot_events.data_source_enabled": true,
                "reboot_events.rate_limiting_settings": {
                    "default_capacity": 5,
                    "default_period_ms": 900000,
                    "max_buckets": 1
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
                }
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
                "http_api.ingress_base_url": "https://ingress2.memfault.com",
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
