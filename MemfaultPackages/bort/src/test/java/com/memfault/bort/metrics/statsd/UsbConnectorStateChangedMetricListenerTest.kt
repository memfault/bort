package com.memfault.bort.metrics.statsd

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.UsbConnectorStateChanged
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsbConnectorStateChangedMetricListenerTest {
    @get:Rule()
    val metricsDbTestEnvironment = MetricsDbTestEnvironment()

    private val listener = UsbConnectorStateChangedMetricListener()

    @Test
    fun reportsConnectAndDisconnect() = runTest {
        listener.reportEventMetric(
            eventTimestampMillis = 5000L,
            eventElapsedRealtimeMillis = 15000L,
            atom = Atom(
                usb_connector_state_changed = UsbConnectorStateChanged(
                    state = UsbConnectorStateChanged.State.STATE_CONNECTED,
                    id = "0001",
                    last_connect_duration_millis = 0,
                ),
            ),
        )
        listener.reportEventMetric(
            eventTimestampMillis = 9000L,
            eventElapsedRealtimeMillis = 19000L,
            atom = Atom(
                usb_connector_state_changed = UsbConnectorStateChanged(
                    state = UsbConnectorStateChanged.State.STATE_DISCONNECTED,
                    id = "0001",
                    last_connect_duration_millis = 4000,
                ),
            ),
        )

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(10000L, 20000L, false)

        assertThat(
            report.hourlyHeartbeatReport.metrics,
        ).isEqualTo(
            mapOf(
                UsbConnectorStateChangedMetricListener.USB_CONNECTED_METRIC_NAME + ".latest" to JsonPrimitive("0"),
                UsbConnectorStateChangedMetricListener.USB_CONNECTIONS_METRIC_NAME + ".sum" to JsonPrimitive(1.0),
                UsbConnectorStateChangedMetricListener.USB_CONNECT_DURATION_METRIC_NAME + ".mean" to
                    JsonPrimitive(4000.0),
                UsbConnectorStateChangedMetricListener.USB_CONNECT_DURATION_METRIC_NAME + ".max" to
                    JsonPrimitive(4000.0),
                UsbConnectorStateChangedMetricListener.USB_CONNECT_DURATION_METRIC_NAME + ".sum" to
                    JsonPrimitive(4000.0),
            ),
        )
    }

    @Test
    fun ignoresUnknownState() = runTest {
        listener.reportEventMetric(
            eventTimestampMillis = 5000L,
            eventElapsedRealtimeMillis = 15000L,
            atom = Atom(
                usb_connector_state_changed = UsbConnectorStateChanged(id = "0001"),
            ),
        )

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(10000L, 20000L, false)

        assertThat(report.hourlyHeartbeatReport.metrics).isEqualTo(emptyMap<String, JsonPrimitive>())
    }
}
