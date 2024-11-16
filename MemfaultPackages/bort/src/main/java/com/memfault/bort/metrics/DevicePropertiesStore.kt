package com.memfault.bort.metrics

import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg

/**
 * Forwards to the metrics service
 *
 * Note: Deliberately not @Inject - this is only supposed to be injected by MetricsCollectionTask, so that it can pass
 * around to any consumers who might be writing metrics. This will avoid anything randomly writing metrics *not*
 * during the execution of the [MetricsCollectionTask].
 *
 * Only properties (i.e. `.latest`) are supported.
 */
class DevicePropertiesStore {
    fun upsert(
        name: String,
        value: String,
        internal: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        Reporting.report().stringProperty(name = name, addLatestToReport = true, internal = internal)
            .update(value, timestamp = timestamp)
    }

    fun upsert(
        name: String,
        value: Double,
        internal: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        Reporting.report().numberProperty(name = name, addLatestToReport = true, internal = internal)
            .update(value, timestamp = timestamp)
    }

    fun upsert(
        name: String,
        value: Long,
        internal: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        upsert(name = name, value = value.toDouble(), internal = internal, timestamp = timestamp)
    }

    fun upsert(
        name: String,
        value: Int,
        internal: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        upsert(name = name, value = value.toDouble(), internal = internal, timestamp = timestamp)
    }

    fun upsert(
        name: String,
        value: Boolean,
        internal: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        Reporting.report()
            .boolStateTracker(name = name, aggregations = listOf(StateAgg.LATEST_VALUE), internal = internal)
            .state(value, timestamp = timestamp)
    }
}
