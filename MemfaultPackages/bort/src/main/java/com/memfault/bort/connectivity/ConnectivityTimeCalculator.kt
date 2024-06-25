package com.memfault.bort.connectivity

import androidx.annotation.VisibleForTesting
import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.reporting.DataType.DOUBLE
import com.memfault.bort.reporting.MetricType.GAUGE
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Duration.Companion.seconds

object ConnectivityTimeCalculator : CalculateDerivedAggregations {

    override fun calculate(
        startTimestampMs: Long,
        endTimestampMs: Long,
        metrics: Map<String, JsonPrimitive>,
        internalMetrics: Map<String, JsonPrimitive>,
    ): List<DerivedAggregation> = calculateConnectedTimeMetrics(
        startTimestampMs = startTimestampMs,
        endTimestampMs = endTimestampMs,
        heartbeatReportMetrics = metrics + internalMetrics,
    )

    @VisibleForTesting
    internal fun calculateConnectedTimeMetrics(
        startTimestampMs: Long?,
        endTimestampMs: Long?,
        heartbeatReportMetrics: Map<String, JsonPrimitive>,
    ): List<DerivedAggregation> {
        if (startTimestampMs == null || endTimestampMs == null || heartbeatReportMetrics.isEmpty()) {
            return emptyList()
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

            return listOf(
                connectivityTimeDerivedAggregation(
                    metricName = EXPECTED_TIME_METRIC,
                    metricValue = totalSecs.inWholeMilliseconds.toDouble(),
                    collectionTimeMs = endTimestampMs,
                ),
                connectivityTimeDerivedAggregation(
                    metricName = CONNECTED_TIME_METRIC,
                    metricValue = connectedSecs.inWholeMilliseconds.toDouble(),
                    collectionTimeMs = endTimestampMs,
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun connectivityTimeDerivedAggregation(
        metricName: String,
        metricValue: Double,
        collectionTimeMs: Long,
    ) = DerivedAggregation.create(
        metricName = metricName,
        metricValue = metricValue,
        collectionTimeMs = collectionTimeMs,
        metricType = GAUGE,
        dataType = DOUBLE,
        internal = false,
    )

    @VisibleForTesting
    internal const val CONNECTED_TIME_METRIC = "connectivity_connected_time_ms"

    @VisibleForTesting
    internal const val EXPECTED_TIME_METRIC = "connectivity_expected_time_ms"
}
