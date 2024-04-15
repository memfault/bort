package com.memfault.bort.connectivity

import androidx.annotation.VisibleForTesting
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class ConnectivityTimeResults(
    val hrtRollup: Set<HighResTelemetry.Rollup>,
    val heartbeatMetrics: Map<String, JsonPrimitive>,
) {
    companion object {
        val EMPTY = ConnectivityTimeResults(hrtRollup = emptySet(), heartbeatMetrics = emptyMap())
    }
}

class ConnectivityTimeCalculator
@Inject constructor() {
    fun calculateConnectedTimeMetrics(
        startTimestampMs: Long?,
        endTimestampMs: Long?,
        heartbeatReportMetrics: Map<String, JsonPrimitive>,
    ): ConnectivityTimeResults {
        if (startTimestampMs == null || endTimestampMs == null || heartbeatReportMetrics.isEmpty()) {
            return ConnectivityTimeResults.EMPTY
        }

        // "Not connected" connectivity types.
        val noneSecs = heartbeatReportMetrics["connectivity.type_NONE.total_secs"]?.doubleOrNull
        val unknownSecs = heartbeatReportMetrics["connectivity.type_UNKNOWN.total_secs"]?.doubleOrNull

        // "Connected" connectivity types.
        val wifiSecs = heartbeatReportMetrics["connectivity.type_WIFI.total_secs"]?.doubleOrNull
        val cellSecs = heartbeatReportMetrics["connectivity.type_CELLULAR.total_secs"]?.doubleOrNull
        val ethSecs = heartbeatReportMetrics["connectivity.type_ETHERNET.total_secs"]?.doubleOrNull
        val bluetoothSecs = heartbeatReportMetrics["connectivity.type_BLUETOOTH.total_secs"]?.doubleOrNull

        val totalSecs = listOfNotNull(
            noneSecs,
            unknownSecs,
            wifiSecs,
            cellSecs,
            ethSecs,
            bluetoothSecs,
        ).sum().seconds

        return if (totalSecs >= 1.seconds) {
            val connectedSecs = listOfNotNull(wifiSecs, cellSecs, ethSecs, bluetoothSecs).sum().seconds

            return ConnectivityTimeResults(
                hrtRollup = setOf(
                    connectivityTimeHrtRollup(
                        metricName = EXPECTED_TIME_METRIC,
                        metricValue = totalSecs.inWholeMilliseconds.toDouble(),
                        collectionTimeMs = endTimestampMs,
                        internal = false,
                    ),
                    connectivityTimeHrtRollup(
                        metricName = CONNECTED_TIME_METRIC,
                        metricValue = connectedSecs.inWholeMilliseconds.toDouble(),
                        collectionTimeMs = endTimestampMs,
                        internal = false,
                    ),
                ),
                heartbeatMetrics = mapOf(
                    EXPECTED_TIME_METRIC to JsonPrimitive(totalSecs.inWholeMilliseconds.toDouble()),
                    CONNECTED_TIME_METRIC to JsonPrimitive(connectedSecs.inWholeMilliseconds.toDouble()),
                ),
            )
        } else {
            ConnectivityTimeResults.EMPTY
        }
    }

    private fun connectivityTimeHrtRollup(
        metricName: String,
        metricValue: Double,
        collectionTimeMs: Long,
        internal: Boolean,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = metricName,
            metricType = Gauge,
            dataType = DoubleType,
            internal = internal,
        ),
        data = listOf(Datum(t = collectionTimeMs, value = JsonPrimitive(metricValue))),
    )

    companion object {
        @VisibleForTesting
        internal const val CONNECTED_TIME_METRIC = "connectivity_connected_time_ms"

        @VisibleForTesting
        internal const val EXPECTED_TIME_METRIC = "connectivity_expected_time_ms"
    }
}
