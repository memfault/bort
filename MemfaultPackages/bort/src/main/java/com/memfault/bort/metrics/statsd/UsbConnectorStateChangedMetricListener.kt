package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.UsbConnectorStateChanged
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class UsbConnectorStateChangedMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        eventElapsedRealtimeMillis: Long,
        atom: Atom,
    ) {
        if (atom.usb_connector_state_changed != null) {
            val usbConnectorStateChanged = atom.usb_connector_state_changed
            val connected = when (usbConnectorStateChanged.state) {
                UsbConnectorStateChanged.State.STATE_CONNECTED -> true
                UsbConnectorStateChanged.State.STATE_DISCONNECTED -> false
                else -> return
            }

            Reporting.report().boolStateTracker(
                name = USB_CONNECTED_METRIC_NAME,
                aggregations = listOf(StateAgg.LATEST_VALUE),
            ).state(connected, timestamp = eventTimestampMillis, uptime = eventElapsedRealtimeMillis)

            if (connected) {
                Reporting.report().counter(
                    name = USB_CONNECTIONS_METRIC_NAME,
                ).increment(timestamp = eventTimestampMillis, uptime = eventElapsedRealtimeMillis)
            } else {
                // Only set on disconnect: it is 0 while the port is connected.
                val connectDurationMillis = usbConnectorStateChanged.last_connect_duration_millis ?: 0
                if (connectDurationMillis > 0) {
                    Reporting.report().distribution(
                        name = USB_CONNECT_DURATION_METRIC_NAME,
                        aggregations = listOf(NumericAgg.MEAN, NumericAgg.MAX, NumericAgg.SUM),
                    ).record(
                        value = connectDurationMillis,
                        timestamp = eventTimestampMillis,
                        uptime = eventElapsedRealtimeMillis,
                    )
                }
            }
        }
    }

    override fun atoms(): Set<Int> = setOf(USB_CONNECTOR_STATE_CHANGED_ATOM_ID)

    companion object {
        internal const val USB_CONNECTED_METRIC_NAME = "usb.connected"
        internal const val USB_CONNECTIONS_METRIC_NAME = "usb.connections"
        internal const val USB_CONNECT_DURATION_METRIC_NAME = "usb.connect_duration_ms"
        private const val USB_CONNECTOR_STATE_CHANGED_ATOM_ID = 70
    }
}
