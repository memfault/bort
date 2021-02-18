package com.memfault.bort.settings

import com.memfault.bort.BortJson
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.time.boxed
import io.mockk.every
import io.mockk.mockk
import kotlin.time.milliseconds
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DynamicSettingsProviderTest {
    @Test
    fun testParseRemoteSettings() {
        assertEquals(
            FetchedSettings(
                batteryStatsDataSourceEnabled = false,
                bortMinLogLevel = 2,
                bortSettingsUpdateInterval = 1234.milliseconds.boxed(),
                bugReportCollectionInterval = 1234.milliseconds.boxed(),
                bugReportDataSourceEnabled = false,
                bugReportFirstBugReportDelayAfterBoot = 5678.milliseconds.boxed(),
                bugReportRequestRateLimitingSettings = RateLimitingSettings(
                    defaultCapacity = 3,
                    defaultPeriod = 1800000.milliseconds.boxed(),
                    maxBuckets = 1,
                ),
                bugReportMaxUploadAttempts = 15,
                bugReportOptionsMinimal = true,
                deviceInfoAndroidBuildVersionKey = "ro.build.another",
                deviceInfoAndroidBuildVersionSource = "build_fingerprint",
                deviceInfoAndroidDeviceSerialKey = "ro.another.serial",
                deviceInfoAndroidHardwareVersionKey = "ro.product.hw",
                dropBoxAnrsRateLimitingSettings = RateLimitingSettings(
                    defaultCapacity = 10,
                    defaultPeriod = 900000.milliseconds.boxed(),
                    maxBuckets = 1,
                ),
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
                logcatCollectionInterval = 9999.milliseconds.boxed(),
                logcatDataSourceEnabled = false,
                logcatFilterSpecs = listOf(
                    LogcatFilterSpec(priority = LogcatPriority.WARN, tag = "*"),
                    LogcatFilterSpec(priority = LogcatPriority.VERBOSE, tag = "bort"),
                ),
                metricsCollectionInterval = 91011.milliseconds.boxed(),
                metricsDataSourceEnabled = false,
                rebootEventsRateLimitingSettings = RateLimitingSettings(
                    defaultCapacity = 5,
                    defaultPeriod = 900000.milliseconds.boxed(),
                    maxBuckets = 1,
                ),
            ),
            SETTINGS_FIXTURE,
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
                SETTINGS_FIXTURE.copy(bortMinLogLevel = LogLevel.VERBOSE.level) andThen
                SETTINGS_FIXTURE.copy(bortMinLogLevel = LogLevel.INFO.level)
        }

        val settings = DynamicSettingsProvider(storedSettingsPreferenceProvider)

        // The first call will use the value in resources
        assertEquals(LogLevel.VERBOSE, settings.minLogLevel)

        // The second call will still operate on a cached settings value
        assertEquals(LogLevel.VERBOSE, settings.minLogLevel)

        // This will cause settings to be reconstructed on the next call, which will
        // read the new min_log_level from shared preferences
        settings.invalidate()
        assertEquals(LogLevel.INFO, settings.minLogLevel)
    }
}

internal val SETTINGS_FIXTURE = """
            {
              "data": {
                "battery_stats.data_source_enabled": false,
                "bort.min_log_level": 2,
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
                    {"type": "text_credential"}
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
                "drop_box.excluded_tags": [],
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
                "logcat.collection_interval_ms": 9999,
                "logcat.data_source_enabled": False,
                "logcat.filter_specs": [{"priority": "W", "tag": "*"}, {"priority": "V", "tag": "bort"}],
                "metrics.collection_interval_ms": 91011,
                "metrics.data_source_enabled": false
                "reboot_events.rate_limiting_settings": {
                    "default_capacity": 5,
                    "default_period_ms": 900000,
                    "max_buckets": 1
                }
              }
            }
""".trimIndent().toSettings()

fun String.toSettings() =
    BortJson.decodeFromString(FetchedSettings.FetchedSettingsContainer.serializer(), this).data
