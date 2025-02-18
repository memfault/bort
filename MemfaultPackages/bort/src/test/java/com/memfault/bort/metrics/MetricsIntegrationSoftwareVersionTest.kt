package com.memfault.bort.metrics

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.database.DbReport
import com.memfault.bort.reporting.Reporting
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MetricsIntegrationSoftwareVersionTest {

    @get:Rule()
    val metricsDbTestEnvironment = MetricsDbTestEnvironment()

    private val dao: CustomMetrics get() = metricsDbTestEnvironment.dao

    @Test
    fun checkSoftwareVersionAtStart() = runTest {
        assertThat(dao.startedHeartbeatOrNull()).isNull()
    }

    @Test
    fun checkSoftwareVersionReport() = runTest {
        Reporting.report().counter("count").increment()
        assertThat(dao.startedHeartbeatOrNull())
            .isNotNull()
            .prop(DbReport::softwareVersion)
            .isEqualTo(metricsDbTestEnvironment.deviceInfo.softwareVersion)
    }

    @Test
    fun checkEmptyReportSoftwareVersion() = runTest {
        assertThat(dao.collectHeartbeat(endTimestampMs = System.currentTimeMillis())).all {
            prop(CustomReport::hourlyHeartbeatReport)
                .prop(MetricReport::softwareVersion)
                .isNull()
        }
    }

    @Test
    fun checkSoftwareVersionReportAfterCollection() = runTest {
        metricsDbTestEnvironment.deviceInfo = metricsDbTestEnvironment.deviceInfo.copy(
            softwareVersion = "1",
        )

        Reporting.report().counter("count").increment()

        metricsDbTestEnvironment.deviceInfo = metricsDbTestEnvironment.deviceInfo.copy(
            softwareVersion = "2",
        )

        assertThat(dao.startedHeartbeatOrNull())
            .isNotNull()
            .prop(DbReport::softwareVersion)
            .isEqualTo("1")

        assertThat(dao.collectHeartbeat(endTimestampMs = System.currentTimeMillis())).all {
            prop(CustomReport::hourlyHeartbeatReport)
                .prop(MetricReport::softwareVersion)
                .isEqualTo("1")
        }

        assertThat(dao.startedHeartbeatOrNull())
            .isNull()
    }

    @Test
    fun forceEndAllReports() = runTest {
        metricsDbTestEnvironment.dailyHeartbeatEnabledValue = true
        metricsDbTestEnvironment.deviceInfo = metricsDbTestEnvironment.deviceInfo.copy(
            softwareVersion = "force-end",
        )

        Reporting.report().counter("count").increment()
        Reporting.session("session").apply {
            start()
            counter("count").incrementBy(2)
        }

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = System.currentTimeMillis(),
                forceEndAllReports = true,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::softwareVersion).isEqualTo("force-end")
                prop(MetricReport::metrics).containsOnly(
                    "count.sum" to JsonPrimitive(1.0),
                )
            }

            prop(CustomReport::dailyHeartbeatReport).isNotNull().all {
                prop(MetricReport::softwareVersion).isEqualTo("force-end")
                prop(MetricReport::metrics).containsOnly(
                    "count.sum" to JsonPrimitive(1.0),
                )
            }

            prop(CustomReport::sessions).single().all {
                prop(MetricReport::softwareVersion).isEqualTo("force-end")
                prop(MetricReport::metrics).containsOnly(
                    "count.sum" to JsonPrimitive(2.0),
                )
            }
        }
    }
}
