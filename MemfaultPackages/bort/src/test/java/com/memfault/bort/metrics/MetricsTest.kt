package com.memfault.bort.metrics

import com.memfault.bort.makeFakeSharedPreferences
import org.junit.Test

class BuiltinMetricsStoreTest {

    @Test
    fun testMetricTumble() {
        val registry = makeFakeMetricRegistry()
        val store = BuiltinMetricsStore(registry)

        store.increment("crashes_counter")
        store.increment("crashes_counter")
        assert(store.collect("crashes_counter") == 2.0f)

        // collect mutates and tumbles the value
        assert(store.collect("crashes_counter") == 0.0f)
    }

    @Test
    fun testCollection() {
        val registry = makeFakeMetricRegistry()
        val store = BuiltinMetricsStore(registry)

        // Increment a few keys
        listOf("a", "b", "c", "d")
            .forEach { store.increment(it) }

        assert(
            store.collectMetrics() == mapOf(
                "a" to 1.0f,
                "b" to 1.0f,
                "c" to 1.0f,
                "d" to 1.0f,
            )
        )

        // After collecting, the metrics will tumble (see the test above), and hence
        // will be absent from collection.
        assert(store.collectMetrics() == mapOf<String, Float>())
    }

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

fun makeFakeMetricRegistry(): MetricRegistry =
    SharedPreferencesMetricRegistry(makeFakeSharedPreferences())
