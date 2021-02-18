package com.memfault.bort.buildsrc

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths

const val BORT_SETTINGS_RESOURCE_NAME = "settings.json"

class BortSettingsDownloaderException(msg: String, cause: Throwable?) : Exception(msg, cause) {
    constructor(msg: String) : this(msg, null)
}

fun getBortSettingsAssetsPath(rootDir: File) =
    Paths.get(rootDir.absolutePath, "settings").toString()

fun getBortSettingsAssetsFile(rootDir: File) =
    Paths.get(getBortSettingsAssetsPath(rootDir), BORT_SETTINGS_RESOURCE_NAME).toFile()

fun downloadSettings(deviceBaseUrl: String, projectKey: String, softwareType: String = "android-build"): String {
    val url = URL("$deviceBaseUrl/api/v0/sdk-settings?software_type=$softwareType")
    val connection = try {
        url.openConnection().apply {
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Memfault-Project-Key", projectKey)
        } as HttpURLConnection
    } catch (e: Exception) {
        throw BortSettingsDownloaderException("Failed to connect to Memfault Server: $url", e)
    }
    val responseCode = try {
        connection.responseCode
    } catch (e: Exception) {
        throw BortSettingsDownloaderException("Failed to connect to Memfault Server: $url", e)
    }
    when (responseCode) {
        401 -> throw BortSettingsDownloaderException(
            """Memfault Server Error: ${connection.responseMessage}
                   Please ensure MEMFAULT_PROJECT_API_KEY in bort.properties matches your project's settings.
            """.trimIndent()
        )
        in 400..599 -> throw BortSettingsDownloaderException(
            "Memfault Server Error: ${connection.responseMessage}"
        )
    }
    return connection.inputStream.use {
        it.bufferedReader().readText()
    }
}

fun refreshSettings(
    rootDir: File,
    warn: (String, Exception?) -> Unit,
    getConfig: () -> String
) {
    val generatedFolder = File(getBortSettingsAssetsPath(rootDir))
    generatedFolder.mkdirs()
    val generatedFile = getBortSettingsAssetsFile(rootDir)
    val text = try {
        // TODO: we should be validating this with the same FetchedSettings  that is used in runtime but Gradle 6.x
        //  uses Kotlin 1.3.x for buildSrc and using it with kotlinx-serialization resulted in internal compiler errors,
        //  Revisit this later. For now, we'll validate that json is well-formed.
        getConfig().also {
            // will throw if invalid
            JSONObject(it)
        }
    } catch (e: Exception) {
        if (!generatedFile.isFile) {
            throw BortSettingsDownloaderException("Failed to fetch initial configuration from Memfault Server", e)
        }
        warn("Failed to refresh configuration from Memfault Server, reusing existing one...", e)
        return
    }
    generatedFile.writeText(text)
}

fun fetchBortSettings(
    rootDir: File,
    warn: (String, Exception?) -> Unit,
    deviceBaseUrl: String,
    projectKey: String,
    shouldFetch: Boolean,
    getDefaultProperty: (String) -> String?
) {
    val getConfig = {
        if (shouldFetch) {
            downloadSettings(
                deviceBaseUrl = deviceBaseUrl,
                projectKey = projectKey
            )
        } else {
            // Note: this happens in CI use cases
            """
            {
                "data": {
                   "battery_stats.data_source_enabled" : true,
                   "bort.min_log_level" : ${getDefaultProperty("MINIMUM_LOG_LEVEL")?.toInt() ?: 5},
                   "bort.settings_update_interval_ms" : 86400000,
                   "bug_report.collection_interval_ms" : 43200000,
                   "bug_report.data_source_enabled" : true,
                   "bug_report.first_bug_report_delay_after_boot_ms" : 600000,
                   "bug_report.max_upload_attempts" : 3,
                   "bug_report.options.minimal" : false,
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
                   "device_info.android_build_version_key" : "${getDefaultProperty("ANDROID_BUILD_VERSION_KEY") ?: "ro.build.date.utc"}",
                   "device_info.android_build_version_source" : "${getDefaultProperty("ANDROID_BUILD_VERSION_SOURCE") ?: "build_fingerprint_and_system_property"}",
                   "device_info.android_device_serial_key" : "${getDefaultProperty("ANDROID_DEVICE_SERIAL_KEY") ?: "ro.serialno"}",
                   "device_info.android_hardware_version_key" : "${getDefaultProperty("ANDROID_HARDWARE_VERSION_KEY") ?: "ro.product.board"}",
                   "drop_box.anrs.rate_limiting_settings": {
                       "default_capacity": 10,
                       "default_period_ms": 900000,
                       "max_buckets": 1
                   },
                   "drop_box.data_source_enabled" : ${getDefaultProperty("DATA_SOURCE_CALIPER_DROP_BOX_TRACES_ENABLED")?.toBoolean() ?: false},
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
                   "http_api.device_base_url" : "${getDefaultProperty("MEMFAULT_API_BASE_URL") ?: "https://api.memfault.com"}",
                   "http_api.files_base_url" : "${getDefaultProperty("MEMFAULT_FILES_BASE_URL") ?: "https://files.memfault.com"}",
                   "http_api.ingress_base_url" : "${getDefaultProperty("MEMFAULT_INGRESS_BASE_URL") ?: "https://ingress.memfault.com"}",
                   "http_api.upload_compression_enabled" : true,
                   "http_api.upload_network_constraint_allow_metered_connection" : true,
                   "logcat.collection_interval_ms" : 900000,
                   "logcat.data_source_enabled" : true,
                   "logcat.filter_specs": [{"priority": "W", "tag": "*"}],
                   "metrics.collection_interval_ms" : 3600000,
                   "metrics.data_source_enabled" : true,
                   "reboot_events.rate_limiting_settings": {
                       "default_capacity": 5,
                       "default_period_ms": 900000,
                       "max_buckets": 1
                   }
                }
            }
            """.trimIndent()
        }
    }
    refreshSettings(
        rootDir = rootDir,
        warn = warn,
        getConfig = getConfig
    )
}
