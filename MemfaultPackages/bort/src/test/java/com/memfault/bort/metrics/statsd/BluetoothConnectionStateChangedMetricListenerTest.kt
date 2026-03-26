package com.memfault.bort.metrics.statsd

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.BluetoothConnectionStateChanged
import com.memfault.bort.metrics.statsd.proto.ConnectionStateEnum.CONNECTION_STATE_DISCONNECTED
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BluetoothConnectionStateChangedMetricListenerTest {
    @get:Rule()
    val metricsDbTestEnvironment = MetricsDbTestEnvironment()

    @Test
    fun producesBluetoothDisconnectMetricsCorrectly() = runTest {
        val listener = BluetoothConnectionStateChangedMetricListener()
        val bluetoothConnectionStateChanged = BluetoothConnectionStateChanged(
            state = CONNECTION_STATE_DISCONNECTED,
            connection_reason = 1337,
        )
        listener.reportEventMetric(
            eventTimestampMillis = 10000L,
            eventElapsedRealtimeMillis = 20000L,
            atom = Atom(
                bluetooth_connection_state_changed = bluetoothConnectionStateChanged,
            ),
        )

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(10000L, 20000L, false)
        assertThat(report.hourlyHeartbeatReport.metrics["bluetooth.disconnect.latest"])
            .isEqualTo(JsonPrimitive(bluetoothConnectionStateChanged.toString()))
        assertThat(report.hourlyHeartbeatReport.metrics["bluetooth.disconnect_reason.latest"])
            .isEqualTo(JsonPrimitive("1337"))
        assertThat(report.hourlyHeartbeatReport.metrics["bluetooth.disconnects.sum"])
            .isEqualTo(JsonPrimitive(1.0))
    }
}
