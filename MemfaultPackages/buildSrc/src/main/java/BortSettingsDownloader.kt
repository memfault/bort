package com.memfault.bort.buildsrc

import com.fasterxml.jackson.core.JsonGenerationException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.flipkart.zjsonpatch.JsonDiff
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths

const val BORT_SETTINGS_RESOURCE_NAME = "settings.json"

class BortSettingsDownloaderException(msg: String, cause: Throwable?) : Exception(msg, cause) {
    constructor(msg: String) : this(msg, null)
}

class BortSettingsInconsistencyException(msg: String, cause: Throwable?) : Exception(msg, cause) {
    constructor(msg: String) : this(msg, null)
}

fun getBortSettingsAssetsPath(rootDir: File): String =
    Paths.get(rootDir.absolutePath, "settings").toString()

fun getBortSettingsAssetsFile(rootDir: File): File =
    Paths.get(getBortSettingsAssetsPath(rootDir), BORT_SETTINGS_RESOURCE_NAME).toFile()

private fun fetchBortConfig(deviceBaseUrl: String, projectKey: String, softwareType: String = "android-build"): String {
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

private fun generateDevConfig(
    getDefaultProperty: (String) -> String?
): String =
    """
            {
                "data": {
                   "battery_stats.data_source_enabled" : true,
                   "battery_stats.command_timeout_ms" : 60000,
                   "bort.min_log_level" : ${getDefaultProperty("MINIMUM_LOG_LEVEL")?.toInt() ?: 5},
                   "bort.event_log_enabled": true,
                   "bort.settings_update_interval_ms" : 86400000,
                   "bug_report.collection_interval_ms" : 43200000,
                   "bug_report.data_source_enabled" : true,
                   "bug_report.first_bug_report_delay_after_boot_ms" : 600000,
                   "bug_report.max_storage_bytes" : 50000000,
                   "bug_report.max_stored_age_ms" : 0,
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
                   "drop_box.data_source_enabled" : ${getDefaultProperty("DATA_SOURCE_CALIPER_DROP_BOX_TRACES_ENABLED")?.toBoolean() ?: true},
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
                   "http_api.device_base_url" : "${getDefaultProperty("MEMFAULT_API_BASE_URL") ?: "https://api.memfault.com"}",
                   "http_api.files_base_url" : "${getDefaultProperty("MEMFAULT_FILES_BASE_URL") ?: "https://files.memfault.com"}",
                   "http_api.ingress_base_url" : "${getDefaultProperty("MEMFAULT_INGRESS_BASE_URL") ?: "https://ingress.memfault.com"}",
                   "http_api.upload_compression_enabled" : true,
                   "http_api.upload_network_constraint_allow_metered_connection" : true,
                   "http_api.connect_timeout_ms": 30000,
                   "http_api.write_timeout_ms": 0,
                   "http_api.read_timeout_ms": 0,
                   "http_api.call_timeout_ms": 0,
                   "logcat.collection_interval_ms" : 900000,
                   "logcat.command_timeout_ms" : 60000,
                   "logcat.data_source_enabled" : true,
                   "logcat.filter_specs": [{"priority": "W", "tag": "*"}],
                   "metrics.collection_interval_ms" : 3600000,
                   "metrics.data_source_enabled" : true,
                   "package_manager.command_timeout_ms" : 60000,
                   "reboot_events.data_source_enabled": true,
                   "reboot_events.rate_limiting_settings": {
                       "default_capacity": 5,
                       "default_period_ms": 900000,
                       "max_buckets": 1
                   },
                   "structured_log.data_source_enabled": true,
                   "structured_log.dump_period_ms": 7200000,
                   "structured_log.max_message_size_bytes": 4096,
                   "structured_log.min_storage_threshold_bytes": 268435456,
                   "structured_log.num_events_before_dump": 1000,
                   "structured_log.rate_limiting_settings": {
                       "default_capacity": 1000,
                       "default_period_ms": 15000,
                       "max_buckets": 1
                   }
                }
            }
    """.trimIndent()

private fun readExistingConfig(rootDir: File): String? =
    getBortSettingsAssetsFile(rootDir).let {
        if (it.exists()) it.readText()
        else null
    }

private fun hasSameJsonContent(remote: String, existing: String): Boolean =
    ObjectMapper().let {
        it.readTree(remote) == it.readTree(existing)
    }

private fun writeConfig(rootDir: File, contents: String, prettyPrint: Boolean = true) {
    val mapper = ObjectMapper().also {
        if (prettyPrint) it.enable(SerializationFeature.INDENT_OUTPUT)
    }

    try {
        val jsonTree = mapper.readTree(contents)

        File(getBortSettingsAssetsPath(rootDir)).mkdirs()
        mapper.writeValue(getBortSettingsAssetsFile(rootDir), jsonTree)
    } catch (ex: Exception) {
        when (ex) {
            is JsonMappingException,
            is JsonProcessingException,
            is JsonGenerationException -> {
                throw BortSettingsInconsistencyException("Error when validating JSON config.", ex)
            }
            else -> throw ex
        }
    }
}

private fun showDiff(current: String, future: String, handler: (String) -> Unit, prettyPrint: Boolean = true) {
    val mapper = ObjectMapper().also {
        if (prettyPrint) it.enable(SerializationFeature.INDENT_OUTPUT)
    }
    val diff = JsonDiff.asJson(mapper.readTree(current), mapper.readTree(future))
    handler(mapper.writeValueAsString(diff))
}

fun fetchBortSettingsInternal(
    rootDir: File,
    useDevConfig: Boolean,
    skipDownload: Boolean,
    getDefaultProperty: (String) -> String?,
    warn: (String, Exception?) -> Unit,
    fetchBortConfigFun: () -> String
) {
    if (useDevConfig) {
        writeConfig(rootDir, generateDevConfig(getDefaultProperty))
        return
    }

    val currentConfig = readExistingConfig(rootDir)
    val remoteConfig =
        if (skipDownload) null
        else fetchBortConfigFun()

    if (currentConfig == null && remoteConfig == null) {
        throw BortSettingsInconsistencyException(
            """No local config and SKIP_DOWNLOAD_SETTINGS_JSON is enabled, please
            |provide a local settings file in ${getBortSettingsAssetsFile(rootDir)} or enable settings download
        """.trimMargin()
        )
    } else if (currentConfig != null && remoteConfig != null) {
        if (!hasSameJsonContent(currentConfig, remoteConfig)) {
            warn("Diff between local configuration and project configuration in Memfault servers:", null)
            showDiff(currentConfig, remoteConfig, { diff: String -> warn(diff, null) })
            throw BortSettingsInconsistencyException(
                """Local config (${getBortSettingsAssetsFile(rootDir)}) content
                | is different from settings downloaded from the Memfault server. If the new remote settings look good
                | remove the local settings file and retry. Otherwise, change the remote settings to match local ones
                | and retry.
            """.trimMargin()
            )
        }
    } else if (remoteConfig != null) {
        writeConfig(rootDir, remoteConfig)
    }
}

fun fetchBortSettings(
    rootDir: File,
    deviceBaseUrl: String,
    projectKey: String,
    useDevConfig: Boolean,
    skipDownload: Boolean,
    getDefaultProperty: (String) -> String?,
    warn: (String, Exception?) -> Unit
) {
    fetchBortSettingsInternal(
        rootDir,
        useDevConfig,
        skipDownload,
        getDefaultProperty,
        warn,
        { fetchBortConfig(deviceBaseUrl, projectKey) }
    )
}
