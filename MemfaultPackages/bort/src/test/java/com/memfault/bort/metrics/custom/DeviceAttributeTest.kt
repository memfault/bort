package com.memfault.bort.metrics.custom

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.NumericAgg
import org.junit.Test

/**
 * Pins which metrics count as device attributes, i.e. which keep being reported at a visibility level that collects
 * nothing but [com.memfault.bort.settings.CollectedData.DEVICE_PROPERTIES].
 */
class DeviceAttributeTest {
    private fun metric(
        name: String,
        metricType: MetricType = MetricType.PROPERTY,
    ) = MetricValue(
        name,
        HOURLY_HEARTBEAT_REPORT_TYPE,
        listOf(NumericAgg.LATEST_VALUE),
        false,
        metricType,
        DataType.STRING,
        false,
        123456788,
        188888888,
        "value",
        null,
        null,
        2,
        null,
    )

    @Test
    fun everyPropertyIsAnAttribute() {
        listOf(
            "system_version",
            "android.build_version",
            "bort_version_name",
            "customer.property",
        ).forEach { name ->
            assertThat(metric(name).isDeviceAttribute(), name = name).isTrue()
        }
    }

    @Test
    fun onlyPropertiesAreEverAttributes() {
        // Sync metrics are counters, recorded via successOrFailure, so they are excluded by the metric type alone.
        listOf(MetricType.COUNTER, MetricType.GAUGE, MetricType.EVENT).forEach { type ->
            assertThat(metric("sync_successful", metricType = type), name = type.name)
                .transform { it.isDeviceAttribute() }
                .isFalse()
        }
    }
}
