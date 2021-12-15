package com.memfault.bort.reporting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetricSerializerTest {
    private val testMetric = MetricValue(
        timeMs = 123456,
        reportType = "heartbeat",
        eventName = "metric_a",
        aggregations = listOf(
            NumericAgg.SUM, NumericAgg.COUNT, NumericAgg.MAX, NumericAgg.MEAN, NumericAgg.MIN,
            StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS
        ),
        version = 3
    )

    @Test
    fun testStringMetric() {
        assertEquals(
            """{"version":3,"timestampMs":123456,"reportType":"heartbeat","eventName":"metric_a",""" +
                """"aggregations":["SUM","COUNT","MAX","MEAN","MIN","LATEST_VALUE","TIME_PER_HOUR","TIME_TOTALS"],""" +
                """"value":"abc"}""".trimMargin(),
            testMetric.copy(stringVal = "abc").toJson()
        )
    }

    @Test
    fun testInternalMetric() {
        assertEquals(
            """{"version":3,"timestampMs":123456,"reportType":"heartbeat","eventName":"metric_a",""" +
                """"internal":true,""" +
                """"aggregations":["SUM","COUNT","MAX","MEAN","MIN","LATEST_VALUE","TIME_PER_HOUR","TIME_TOTALS"],""" +
                """"value":"abc"}""".trimMargin(),
            testMetric.copy(stringVal = "abc", internal = true).toJson()
        )
    }

    @Test
    fun testNumberMetric() {
        assertEquals(
            """{"version":3,"timestampMs":123456,"reportType":"heartbeat","eventName":"metric_a",""" +
                """"aggregations":["SUM","COUNT","MAX","MEAN","MIN","LATEST_VALUE","TIME_PER_HOUR","TIME_TOTALS"],""" +
                """"value":3.0}""".trimMargin(),
            testMetric.copy(numberVal = 3.0).toJson()
        )
    }

    @Test
    fun testFinishReport() {
        assertEquals(
            """{"version":1,"timestampMs":12345,"reportType":"heartbeat"}""",
            FinishReport(12345, "heartbeat").toJson()
        )
    }

    @Test
    fun testRollingFinishReport() {
        assertEquals(
            """{"version":1,"timestampMs":12345,"reportType":"heartbeat","startNextReport":true}""",
            FinishReport(12345, "heartbeat", startNextReport = true).toJson()
        )
    }
}
