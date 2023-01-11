package com.memfault.bort.metrics

import org.junit.jupiter.api.Test

class MetricsTest {
    @Test
    fun testMetricNameForTraceTag() {
        val tags = listOf(
            "SYSTEM_TAG",
            "TEST_TAG",
            "RANDOM_TAG"
        )
        val expectation = listOf(
            "drop_box_trace_system_tag_count",
            "drop_box_trace_test_tag_count",
            "drop_box_trace_random_tag_count",
        )

        assert(tags.map { metricForTraceTag(it) }.toList() == expectation)
    }
}
