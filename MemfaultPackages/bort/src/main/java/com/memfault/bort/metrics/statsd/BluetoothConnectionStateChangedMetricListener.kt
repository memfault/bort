package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.ConnectionStateEnum.CONNECTION_STATE_DISCONNECTED
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class BluetoothConnectionStateChangedMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        eventElapsedRealtimeMillis: Long,
        atom: Atom,
    ) {
        if (atom.bluetooth_connection_state_changed != null) {
            val bluetoothConnectionStateChanged = atom.bluetooth_connection_state_changed

            if (bluetoothConnectionStateChanged.state == CONNECTION_STATE_DISCONNECTED) {
                Reporting.report().event(
                    name = BLUETOOTH_DISCONNECT_REPORTED_EVENT,
                    latestInReport = true,
                ).add(
                    value = bluetoothConnectionStateChanged.toString(),
                    timestamp = eventTimestampMillis,
                    uptime = eventElapsedRealtimeMillis,
                )

                Reporting.report().event(
                    name = BLUETOOTH_DISCONNECT_REASON_EVENT,
                    latestInReport = true,
                ).add(
                    value = bluetoothConnectionStateChanged.connection_reason?.toString() ?: "",
                    timestamp = eventTimestampMillis,
                    uptime = eventElapsedRealtimeMillis,
                )

                Reporting.report().counter(
                    name = BLUETOOTH_DISCONNECT_REPORTED_COUNT,
                ).increment(timestamp = eventTimestampMillis, uptime = eventElapsedRealtimeMillis)
            }
        }
    }

    override fun atoms(): Set<Int> = setOf(BLUETOOTH_CONNECTION_STATE_CHANGED_FIELD_ID)

    companion object {
        private const val BLUETOOTH_DISCONNECT_REPORTED_EVENT = "bluetooth.disconnect"
        private const val BLUETOOTH_DISCONNECT_REASON_EVENT = "bluetooth.disconnect_reason"
        private const val BLUETOOTH_DISCONNECT_REPORTED_COUNT = "bluetooth.disconnects"
        private const val BLUETOOTH_CONNECTION_STATE_CHANGED_FIELD_ID = 68
    }
}
