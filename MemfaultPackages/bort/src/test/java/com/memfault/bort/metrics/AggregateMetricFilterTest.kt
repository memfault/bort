package com.memfault.bort.metrics

import com.memfault.bort.metrics.AggregateMetricFilter.filterAndRenameMetrics
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AggregateMetricFilterTest {
    @Test
    fun processMetrics() {
        val input = mapOf(
            "random_metric" to JsonPrimitive(4.32),
            "sysprop.ro.build.type.latest" to JsonPrimitive("user"),
            "version.com.x.y.z.latest" to JsonPrimitive("v1"),
        )
        val output = mapOf(
            "random_metric" to JsonPrimitive(4.32),
            "sysprop.ro.build.type" to JsonPrimitive("user"),
            "version.com.x.y.z" to JsonPrimitive("v1"),
        )
        assertEquals(output, filterAndRenameMetrics(input, internal = false))
    }

    @Test
    fun processInternalMetrics() {
        val input = mapOf(
            "random_metric" to JsonPrimitive(4.32),
            "bort_upstream_version_name.latest" to JsonPrimitive("4.4.0"),
            "sysprop.vendor.memfault.bort.version.sdk.latest" to JsonPrimitive("4.3.1"),
            "request_attempt.sum" to JsonPrimitive(10),
            "usagereporter_version_code.latest" to JsonPrimitive(10),
        )
        val output = mapOf(
            "random_metric" to JsonPrimitive(4.32),
            "bort_upstream_version_name" to JsonPrimitive("4.4.0"),
            "sysprop.vendor.memfault.bort.version.sdk" to JsonPrimitive("4.3.1"),
            "request_attempt" to JsonPrimitive(10),
            "usagereporter_version_code" to JsonPrimitive(10),
        )
        assertEquals(output, filterAndRenameMetrics(input, internal = true))
    }
}
