package com.memfault.bort.metrics

import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import javax.inject.Inject

/**
 * Stores device properties, ready to be uploaded.
 *
 * This is now just a wrapper around custom metric APIs, for convenience.
 */
class DevicePropertiesStore @Inject constructor() {
    fun upsert(name: String, value: String, internal: Boolean = false) {
        Reporting.report().stringProperty(name = name, addLatestToReport = true, internal = internal).update(value)
    }

    fun upsert(name: String, value: Double, internal: Boolean = false) {
        Reporting.report().numberProperty(name = name, addLatestToReport = true, internal = internal).update(value)
    }

    fun upsert(name: String, value: Long, internal: Boolean = false) {
        Reporting.report().numberProperty(name = name, addLatestToReport = true, internal = internal).update(value)
    }

    fun upsert(name: String, value: Int, internal: Boolean = false) {
        Reporting.report().numberProperty(name = name, addLatestToReport = true, internal = internal).update(value)
    }

    fun upsert(name: String, value: Boolean, internal: Boolean = false) {
        Reporting.report()
            .boolStateTracker(name = name, aggregations = listOf(StateAgg.LATEST_VALUE), internal = internal)
            .state(value)
    }
}
