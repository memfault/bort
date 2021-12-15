package com.memfault.bort.metrics

import com.memfault.bort.makeFakeSharedPreferences
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

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
                "a" to JsonPrimitive(1.0f),
                "b" to JsonPrimitive(1.0f),
                "c" to JsonPrimitive(1.0f),
                "d" to JsonPrimitive(1.0f),
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

    @Test
    fun testValueStore() {
        val registry = makeFakeMetricRegistry()
        val store = BuiltinMetricsStore(registry)

        store.addValue("latency", 300f)
        store.addValue("latency", 490f)
        store.addValue("latency", 350f)

        assert(
            store.collectMetrics() == mapOf(
                "latency_count" to JsonPrimitive(3f),
                "latency_sum" to JsonPrimitive(1140f),
                "latency_max" to JsonPrimitive(490f),
                "latency_min" to JsonPrimitive(300f),
            )
        )
    }
}

fun makeFakeMetricRegistry(): MetricRegistry =
    SharedPreferencesMetricRegistry(makeFakeSharedPreferences())
