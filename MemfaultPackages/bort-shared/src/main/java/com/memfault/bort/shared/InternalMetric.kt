package com.memfault.bort.shared

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * An internal metric, sent to Bort from another Memfault app, so that it can be added via BuiltinMetricsStore.
 */
@Serializable
data class InternalMetric(
    /**
     * Metric key. Resulting metric in backend will be 'MemfaultSdkMetric_<key>'
     */
    val key: String,
    /**
     * Value metric: add this value.
     *
     * Only set this for value metrics (i.e. count/sum/max metrics). Equivalent of calling
     * BuiltinMetricsStore.addValue().
     *
     * If null, this will be treated as a standard counting metric. Equivalent of calling
     * BuiltinMetricsStore.increment().
     */
    val value: Float? = null,
    /**
     * Should be written to persistent storage immediately? Passed to parameter of the same name in BuiltinMetricsStore.
     */
    val synchronous: Boolean = false,
) {
    companion object {
        const val INTENT_ACTION_INTERNAL_METRIC = "com.memfault.intent.action.INTERNAL_METRIC"
        private const val CONTENT_VALUES_KEY = "internal_metric"
        private const val RECEIVER_CLASS = "com.memfault.bort.receivers.MetricsReceiver"

        const val OTA_BOOT_COMPLETED = "ota_boot_completed"
        const val OTA_CHECK_ERROR = "ota_check_error"
        const val OTA_CHECK_FOUND_UPDATE = "ota_check_found_update"
        const val OTA_CHECK_NO_UPDATE_AVAILABLE = "ota_check_no_update_available"
        const val OTA_DOWNLOAD_START = "ota_download_start"
        const val OTA_DOWNLOAD_START_RESUMING = "ota_download_start_resuming"
        const val OTA_DOWNLOAD_SUCCESS_SPEED = "ota_download_success_speed_bytes_sec"
        const val OTA_DOWNLOAD_SUCCESS_ERROR = "ota_download_error"
        const val OTA_INSTALL_RECOVERY = "ota_install_recovery"
        const val OTA_INSTALL_RECOVERY_FAILED = "ota_install_recovery_failed"
        const val OTA_INSTALL_RECOVERY_VERIFICATION_FAILED = "ota_install_recovery_verification_failed"

        fun fromIntent(intent: Intent): InternalMetric? = try {
            val json = intent.getStringExtra(CONTENT_VALUES_KEY) ?: ""
            Json.decodeFromString(serializer(), json)
        } catch (ex: SerializationException) { null }

        fun Context.sendMetric(internalMetric: InternalMetric) = sendBroadcast(
            Intent(INTENT_ACTION_INTERNAL_METRIC).apply {
                putExtra(CONTENT_VALUES_KEY, Json.encodeToString(serializer(), internalMetric))
                component = ComponentName.createRelative(BuildConfig.BORT_APPLICATION_ID, RECEIVER_CLASS)
            }
        )
    }
}
