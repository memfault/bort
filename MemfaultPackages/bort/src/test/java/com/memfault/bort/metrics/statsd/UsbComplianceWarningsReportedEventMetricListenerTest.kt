package com.memfault.bort.metrics.statsd

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.ComplianceWarning
import com.memfault.bort.metrics.statsd.proto.UsbComplianceWarningsReported
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsbComplianceWarningsReportedEventMetricListenerTest {
    @get:Rule()
    val metricsDbTestEnvironment = MetricsDbTestEnvironment()

    private val listener = UsbComplianceWarningsReportedEventMetricListener()

    @Test
    fun reportsEachWarning() = runTest {
        listener.reportEventMetric(
            eventTimestampMillis = 5000L,
            eventElapsedRealtimeMillis = 15000L,
            atom = Atom(
                usb_compliance_warnings_reported = UsbComplianceWarningsReported(
                    id = "0001",
                    compliance_warnings = listOf(
                        ComplianceWarning.COMPLIANCE_WARNING_DEBUG_ACCESSORY,
                        ComplianceWarning.COMPLIANCE_WARNING_MISSING_DATA_LINES,
                    ),
                ),
            ),
        )

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(10000L, 20000L, false)

        assertThat(
            report.hourlyHeartbeatReport.metrics,
        ).isEqualTo(
            mapOf(
                UsbComplianceWarningsReportedEventMetricListener.USB_COMPLIANCE_WARNINGS_COUNT + ".sum" to
                    JsonPrimitive(2.0),
                UsbComplianceWarningsReportedEventMetricListener.USB_COMPLIANCE_WARNINGS_EVENT + ".latest" to
                    JsonPrimitive("COMPLIANCE_WARNING_DEBUG_ACCESSORY,COMPLIANCE_WARNING_MISSING_DATA_LINES"),
            ),
        )
    }

    @Test
    fun ignoresEmptyWarnings() = runTest {
        listener.reportEventMetric(
            eventTimestampMillis = 5000L,
            eventElapsedRealtimeMillis = 15000L,
            atom = Atom(
                usb_compliance_warnings_reported = UsbComplianceWarningsReported(id = "0001"),
            ),
        )

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(10000L, 20000L, false)

        assertThat(report.hourlyHeartbeatReport.metrics).isEqualTo(emptyMap<String, JsonPrimitive>())
    }
}
