package com.memfault.bort.reporting

import android.os.RemoteException
import com.memfault.bort.internal.ILogger
import java.lang.IllegalArgumentException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Logging daemon over AIDL
 */
object RemoteMetricsService {
    private const val TAG = "RemoteMetricsService"

    /**
     * Record the metric value inside the metrics (structured logging?) daemon/service:
     *
     * - Update the metric definition (including storing how to aggregate this metric, overriding any previous
     *   configuration).
     * - Record this value in database.
     */
    internal fun record(event: MetricValue) {
        withRemoteLogger { logger ->
            logger.addValue(event.toJson())
        }
    }

    /**
     * Finishes this report inside metrics daemon/service. Only called from Bort heartbeat code, initially.
     *
     * - Performs aggregate calculations based on stored metric values for the report.
     * - Wipes all values for the report from database.
     * - TBD: maybe leaves counter values, but reset to zero?
     * - Uploads aggregate metrics as a custom event, to be inserted into custom_metric_readings etc.
     */
    internal fun finishReport(name: String, endTimeMs: Long, startNextReport: Boolean): Boolean =
        withRemoteLogger { logger ->
            logger.finishReport(FinishReport(endTimeMs, name, startNextReport = startNextReport).toJson())
        }

    private fun withRemoteLogger(block: (logger: ILogger) -> Unit): Boolean =
        try {
            RemoteLogger.get()?.let {
                block(it)
                true
            } ?: run {
                android.util.Log.w(TAG, "Unable to get a handle to ${RemoteLogger.CUSTOM_EVENTD_SERVICE_NAME}")
                false
            }
        } catch (ex: RemoteException) {
            android.util.Log.w(TAG, "Unable to connect to ${RemoteLogger.CUSTOM_EVENTD_SERVICE_NAME}")
            false
        }
}

/**
 * Bump this when the schema changes.
 */
private const val REPORTING_CLIENT_VERSION = 2

private const val VERSION = "version"
private const val TIMESTAMP_MS = "timestampMs"
private const val REPORT_TYPE = "reportType"
private const val START_NEXT_REPORT = "startNextReport"
private const val EVENT_NAME = "eventName"
private const val INTERNAL = "internal"
private const val AGGREGATIONS = "aggregations"
private const val VALUE = "value"
private const val METRIC_TYPE = "metricType"
private const val DATA_TYPE = "dataType"
private const val CARRY_OVER = "carryOver"

/**
 * Every event sent to the remote service contains all information on the event type + how to aggregate.
 */
internal data class MetricValue(
    val timeMs: Long,
    val reportType: String,
    val eventName: String,
    val aggregations: List<AggregationType>,
    // One of these, depending on type.
    val stringVal: String? = null,
    val numberVal: Double? = null,
    val boolVal: Boolean? = null,
    val internal: Boolean = false,
    val version: Int = REPORTING_CLIENT_VERSION,
    val metricType: MetricType,
    val dataType: DataType,
    val carryOverValue: Boolean,
) {
    fun toJson(): String = buildJsonObject {
        put(VERSION, version)
        put(TIMESTAMP_MS, timeMs)
        put(REPORT_TYPE, reportType)
        put(EVENT_NAME, eventName)
        if (internal) {
            put(INTERNAL, true)
        }
        putJsonArray(AGGREGATIONS) {
            for (agg in aggregations) {
                add(agg.toString())
            }
        }
        if (stringVal != null) {
            put(VALUE, stringVal)
        } else if (numberVal != null) {
            put(VALUE, numberVal)
        } else if (boolVal != null) {
            // V2 service supports native booleans, but use strings to support V1.
            put(VALUE, if (boolVal) "1" else "0")
        } else {
            throw IllegalArgumentException("Expected a value to not be null")
        }
        put(METRIC_TYPE, metricType.value)
        put(DATA_TYPE, dataType.value)
        put(CARRY_OVER, carryOverValue)
    }.toString()
}

internal data class FinishReport(
    val timestampMs: Long,
    val reportType: String,
    val version: Int = REPORTING_CLIENT_VERSION,
    val startNextReport: Boolean = false,
) {
    fun toJson(): String = buildJsonObject {
        put(VERSION, version)
        put(TIMESTAMP_MS, timestampMs)
        put(REPORT_TYPE, reportType)
        if (startNextReport) put(START_NEXT_REPORT, true)
    }.toString()
}
