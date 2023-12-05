package com.memfault.bort.connectivity

import com.memfault.bort.connectivity.ConnectivityTimeCalculator.Companion.CONNECTED_TIME_METRIC
import com.memfault.bort.connectivity.ConnectivityTimeCalculator.Companion.EXPECTED_TIME_METRIC
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ConnectivityTimeCalculatorTest {

    private val calculator = ConnectivityTimeCalculator()

    @Test fun `returns EMPTY if any inputs empty`() {
        assert(
            calculator.calculateConnectedTimeMetrics(
                startTimestampMs = null,
                endTimestampMs = null,
                heartbeatReportMetrics = emptyMap(),
            ) == ConnectivityTimeResults.EMPTY,
        )

        assert(
            calculator.calculateConnectedTimeMetrics(
                startTimestampMs = 0,
                endTimestampMs = 1.hours.inWholeMilliseconds,
                heartbeatReportMetrics = emptyMap(),
            ) == ConnectivityTimeResults.EMPTY,
        )

        assert(
            calculator.calculateConnectedTimeMetrics(
                startTimestampMs = 0,
                endTimestampMs = null,
                heartbeatReportMetrics = mapOf(
                    "connectivity.type_NONE.total_secs" to JsonPrimitive(3_600.0),
                ),
            ) == ConnectivityTimeResults.EMPTY,
        )

        assert(
            calculator.calculateConnectedTimeMetrics(
                startTimestampMs = null,
                endTimestampMs = 1.hours.inWholeMilliseconds,
                heartbeatReportMetrics = mapOf(
                    "connectivity.type_NONE.total_secs" to JsonPrimitive(3_600.0),
                ),
            ) == ConnectivityTimeResults.EMPTY,
        )
    }

    @Test fun `returns 1 hour difference`() {
        val results = calculator.calculateConnectedTimeMetrics(
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
        val results = calculator.calculateConnectedTimeMetrics(
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
        val results = calculator.calculateConnectedTimeMetrics(
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
        val results = calculator.calculateConnectedTimeMetrics(
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
        results: ConnectivityTimeResults,
        connectedTimeMs: Long,
        expectedTimeMs: Long,
        collectionTimeMs: Long,
    ) {
        assert(results.heartbeatMetrics.size == 2)
        assert(results.hrtRollup.size == 2)

        assert(results.heartbeatMetrics[CONNECTED_TIME_METRIC]?.double?.toLong() == connectedTimeMs)
        assert(results.heartbeatMetrics[EXPECTED_TIME_METRIC]?.double?.toLong() == expectedTimeMs)

        assert(
            results.hrtRollup.map { it.metadata.stringKey }
                .containsAll(listOf(EXPECTED_TIME_METRIC, CONNECTED_TIME_METRIC)),
        )

        assert(
            results.hrtRollup.single { it.metadata.stringKey == CONNECTED_TIME_METRIC }
                .data[0].value?.double?.toLong() == connectedTimeMs,
        )

        assert(
            results.hrtRollup.single { it.metadata.stringKey == EXPECTED_TIME_METRIC }
                .data[0].value?.double?.toLong() == expectedTimeMs,
        )

        results.hrtRollup.forEach { rollup ->
            assert(!rollup.metadata.internal)
            assert(rollup.metadata.metricType == Gauge)
            assert(rollup.metadata.dataType == DoubleType)
            assert(rollup.data.single().t == collectionTimeMs)
        }
    }
}
