package com.memfault.bort.connectivity

import com.memfault.bort.connectivity.ConnectivitySyncCalculator.Companion.MEMFAULT_SYNC_FAILURE_METRIC
import com.memfault.bort.connectivity.ConnectivitySyncCalculator.Companion.MEMFAULT_SYNC_SUCCESS_METRIC
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Counter
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class ConnectivitySyncCalculatorTest {

    private val calculator = ConnectivitySyncCalculator()

    @Test
    fun `returns EMPTY if any argument is null`() {
        assert(
            calculator.calculateSyncMetrics(
                startTimestampMs = null,
                endTimestampMs = null,
                heartbeatInternalReportMetrics = emptyMap(),
            ) == ConnectivitySyncResults.EMPTY,
        )
    }

    @Test
    fun `success and failure from requests`() {
        val results = calculator.calculateSyncMetrics(
            startTimestampMs = 0,
            endTimestampMs = 1.hours.inWholeMilliseconds,
            heartbeatInternalReportMetrics = mapOf(
                "request_attempt" to JsonPrimitive(5.0),
                "request_failed" to JsonPrimitive(5.0),
            ),
        )

        assertConnectivitySyncResults(
            results = results,
            successCount = 0.0,
            failureCount = 5.0,
            collectionTimeMs = 1.hours.inWholeMilliseconds,
        )
    }

    @Test
    fun `proper calculation requests`() {
        val results = calculator.calculateSyncMetrics(
            startTimestampMs = 0,
            endTimestampMs = 1.hours.inWholeMilliseconds,
            heartbeatInternalReportMetrics = mapOf(
                "request_attempt" to JsonPrimitive(10.0),
                "request_failed" to JsonPrimitive(1.0),
            ),
        )

        assertConnectivitySyncResults(
            results = results,
            successCount = 9.0,
            failureCount = 1.0,
            collectionTimeMs = 1.hours.inWholeMilliseconds,
        )
    }

    @Test
    fun `return empty if attempts are zero or negative`() {
        assert(
            calculator.calculateSyncMetrics(
                startTimestampMs = 0,
                endTimestampMs = 1.hours.inWholeMilliseconds,
                heartbeatInternalReportMetrics = mapOf(
                    "request_attempt" to JsonPrimitive(0.0),
                    "request_failed" to JsonPrimitive(10.0),
                ),
            ) == ConnectivitySyncResults.EMPTY,
        )

        assert(
            calculator.calculateSyncMetrics(
                startTimestampMs = 0,
                endTimestampMs = 1.hours.inWholeMilliseconds,
                heartbeatInternalReportMetrics = mapOf(
                    "request_attempt" to JsonPrimitive(-10.0),
                    "request_failed" to JsonPrimitive(10.0),
                ),
            ) == ConnectivitySyncResults.EMPTY,
        )
    }

    @Test
    fun `return empty if numbers don't make sense`() {
        val results = calculator.calculateSyncMetrics(
            startTimestampMs = 0,
            endTimestampMs = 1.hours.inWholeMilliseconds,
            heartbeatInternalReportMetrics = mapOf(
                "request_attempt" to JsonPrimitive(10.0),
                "request_failed" to JsonPrimitive(11.0),
            ),
        )

        assert(results == ConnectivitySyncResults.EMPTY) { results }
    }

    private fun assertConnectivitySyncResults(
        results: ConnectivitySyncResults,
        successCount: Double,
        failureCount: Double,
        collectionTimeMs: Long,
    ) {
        assert(results.heartbeatMetrics.size == 2)
        assert(results.hrtRollup.size == 2)

        assert(results.heartbeatMetrics[MEMFAULT_SYNC_SUCCESS_METRIC]?.double == successCount)
        assert(results.heartbeatMetrics[MEMFAULT_SYNC_FAILURE_METRIC]?.double == failureCount)

        assert(
            results.hrtRollup.map { it.metadata.stringKey }
                .containsAll(
                    listOf(
                        MEMFAULT_SYNC_FAILURE_METRIC,
                        MEMFAULT_SYNC_SUCCESS_METRIC,
                    ),
                ),
        )

        assert(
            results.hrtRollup.single { it.metadata.stringKey == MEMFAULT_SYNC_SUCCESS_METRIC }
                .data[0].value?.doubleOrNull == successCount,
        )

        assert(
            results.hrtRollup.single { it.metadata.stringKey == MEMFAULT_SYNC_FAILURE_METRIC }
                .data[0].value?.doubleOrNull == failureCount,
        )

        results.hrtRollup.forEach { rollup ->
            assert(!rollup.metadata.internal)
            assert(rollup.metadata.metricType == Counter)
            assert(rollup.metadata.dataType == DoubleType)
            assert(rollup.data.single().t == collectionTimeMs)
        }
    }
}
