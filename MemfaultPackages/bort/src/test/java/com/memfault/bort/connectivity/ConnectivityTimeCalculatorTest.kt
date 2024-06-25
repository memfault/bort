package com.memfault.bort.connectivity

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.memfault.bort.connectivity.ConnectivityTimeCalculator.CONNECTED_TIME_METRIC
import com.memfault.bort.connectivity.ConnectivityTimeCalculator.EXPECTED_TIME_METRIC
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ConnectivityTimeCalculatorTest {

    @Test fun `returns EMPTY if any inputs empty`() {
        assertThat(
            ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
                startTimestampMs = null,
                endTimestampMs = null,
                heartbeatReportMetrics = emptyMap(),
            ),
        ).isEmpty()

        assertThat(
            ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
                startTimestampMs = 0,
                endTimestampMs = 1.hours.inWholeMilliseconds,
                heartbeatReportMetrics = emptyMap(),
            ),
        ).isEmpty()

        assertThat(
            ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
                startTimestampMs = 0,
                endTimestampMs = null,
                heartbeatReportMetrics = mapOf(
                    "connectivity.type_NONE.total_secs" to JsonPrimitive(3_600.0),
                ),
            ),
        ).isEmpty()

        assertThat(
            ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
                startTimestampMs = null,
                endTimestampMs = 1.hours.inWholeMilliseconds,
                heartbeatReportMetrics = mapOf(
                    "connectivity.type_NONE.total_secs" to JsonPrimitive(3_600.0),
                ),
            ),
        ).isEmpty()
    }

    @Test fun `returns 1 hour difference`() {
        val results = ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
            startTimestampMs = 0,
            endTimestampMs = 1.hours.inWholeMilliseconds,
            heartbeatReportMetrics = mapOf(
                "connectivity.type_NONE.total_secs" to JsonPrimitive(3_600.0),
            ),
        )

        assertConnectivityTimeResults(
            results = results,
            connectedTimeMs = 0L,
            expectedTimeMs = 1.hours.inWholeMilliseconds,
            collectionTimeMs = 1.hours.inWholeMilliseconds,
        )
    }

    @Test fun `rounds 30m to 1h`() {
        val results = ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
            startTimestampMs = 0,
            endTimestampMs = 30.minutes.inWholeMilliseconds,
            heartbeatReportMetrics = mapOf(
                "connectivity.type_NONE.total_secs" to JsonPrimitive(1_800.0),
            ),
        )

        assertConnectivityTimeResults(
            results = results,
            connectedTimeMs = 0L,
            expectedTimeMs = 30.minutes.inWholeMilliseconds,
            collectionTimeMs = 30.minutes.inWholeMilliseconds,
        )
    }

    @Test fun `scales to 2 hours`() {
        val results = ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
            startTimestampMs = 0,
            endTimestampMs = 2.hours.inWholeMilliseconds,
            heartbeatReportMetrics = mapOf(
                "connectivity.type_NONE.total_secs" to JsonPrimitive(900.0),
                "connectivity.type_WIFI.total_secs" to JsonPrimitive(2_700.0),
            ),
        )

        assertConnectivityTimeResults(
            results = results,
            connectedTimeMs = 45.minutes.inWholeMilliseconds,
            expectedTimeMs = 60.minutes.inWholeMilliseconds,
            collectionTimeMs = 120.minutes.inWholeMilliseconds,
        )
    }

    @Test fun `returns 25 to 75 connected split in 1 hour`() {
        val results = ConnectivityTimeCalculator.calculateConnectedTimeMetrics(
            startTimestampMs = 0,
            endTimestampMs = 1.hours.inWholeMilliseconds,
            heartbeatReportMetrics = mapOf(
                "connectivity.type_UNKNOWN.total_secs" to JsonPrimitive(900.0),
                "connectivity.type_WIFI.total_secs" to JsonPrimitive(900.0),
                "connectivity.type_ETHERNET.total_secs" to JsonPrimitive(1_800.0),
            ),
        )

        assertConnectivityTimeResults(
            results = results,
            connectedTimeMs = 45.minutes.inWholeMilliseconds,
            expectedTimeMs = 1.hours.inWholeMilliseconds,
            collectionTimeMs = 1.hours.inWholeMilliseconds,
        )
    }

    private fun assertConnectivityTimeResults(
        results: List<DerivedAggregation>,
        connectedTimeMs: Long,
        expectedTimeMs: Long,
        collectionTimeMs: Long,
    ) {
        assertThat(results).hasSize(2)

        assertThat(results[0].metadata.eventName).isEqualTo(EXPECTED_TIME_METRIC)
        assertThat(results[0].value.numberVal?.toLong()).isEqualTo(expectedTimeMs)

        assertThat(results[1].metadata.eventName).isEqualTo(CONNECTED_TIME_METRIC)
        assertThat(results[1].value.numberVal?.toLong()).isEqualTo(connectedTimeMs)

        results.forEach { result ->
            assertThat(result.metadata.internal).isFalse()
            assertThat(result.metadata.metricType).isEqualTo(MetricType.GAUGE)
            assertThat(result.metadata.dataType).isEqualTo(DataType.DOUBLE)
            assertThat(result.value.timestampMs).isEqualTo(collectionTimeMs)
        }
    }
}
