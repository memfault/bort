package com.memfault.bort.connectivity

import androidx.annotation.VisibleForTesting
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Counter
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject

data class ConnectivitySyncResults(
    val hrtRollup: Set<HighResTelemetry.Rollup>,
    val heartbeatMetrics: Map<String, JsonPrimitive>,
) {
    companion object {
        val EMPTY = ConnectivitySyncResults(hrtRollup = emptySet(), heartbeatMetrics = emptyMap())
    }
}

class ConnectivitySyncCalculator
@Inject constructor() {
    fun calculateSyncMetrics(
        startTimestampMs: Long?,
        endTimestampMs: Long?,
        heartbeatInternalReportMetrics: Map<String, JsonPrimitive>,
    ): ConnectivitySyncResults {
        if (startTimestampMs == null || endTimestampMs == null || heartbeatInternalReportMetrics.isEmpty()) {
            return ConnectivitySyncResults.EMPTY
        }

        val requestAttempts = heartbeatInternalReportMetrics["request_attempt"]?.doubleOrNull
            ?.takeIf { it >= -0.1 } ?: 0.0
        val failedRequests = heartbeatInternalReportMetrics["request_failed"]?.doubleOrNull
            ?.takeIf { it >= -0.1 } ?: 0.0

        val successfulRequests = requestAttempts - failedRequests

        val hasAttempts = requestAttempts >= 0.1
        val notMoreFailedRequestsThanAttempts = successfulRequests >= -0.1
        return if (hasAttempts && notMoreFailedRequestsThanAttempts) {
            ConnectivitySyncResults(
                hrtRollup = setOf(
                    connectivitySyncHrtRollup(
                        metricName = MEMFAULT_SYNC_SUCCESS_METRIC,
                        metricValue = successfulRequests,
                        collectionTimeMs = endTimestampMs,
                    ),
                    connectivitySyncHrtRollup(
                        metricName = MEMFAULT_SYNC_FAILURE_METRIC,
                        metricValue = failedRequests,
                        collectionTimeMs = endTimestampMs,
                    ),
                ),
                heartbeatMetrics = mapOf(
                    MEMFAULT_SYNC_SUCCESS_METRIC to JsonPrimitive(successfulRequests),
                    MEMFAULT_SYNC_FAILURE_METRIC to JsonPrimitive(failedRequests),
                ),
            )
        } else {
            ConnectivitySyncResults.EMPTY
        }
    }

    private fun connectivitySyncHrtRollup(
        metricName: String,
        metricValue: Double,
        collectionTimeMs: Long,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = metricName,
            metricType = Counter,
            dataType = DoubleType,
            internal = false,
        ),
        data = listOf(Datum(t = collectionTimeMs, value = JsonPrimitive(metricValue))),
    )

    companion object {
        @VisibleForTesting
        internal const val MEMFAULT_SYNC_SUCCESS_METRIC = "sync_memfault_successful"

        @VisibleForTesting
        internal const val MEMFAULT_SYNC_FAILURE_METRIC = "sync_memfault_failure"
    }
}
