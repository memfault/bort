package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.StatusEnum
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Listener for Bluetooth device RSSI reported events.
 */
@OptIn(ExperimentalStdlibApi::class)
@ContributesMultibinding(scope = SingletonComponent::class)
class BluetoothDeviceRssiReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.bluetooth_device_rssi_reported != null) {
            val rssiEvent = atom.bluetooth_device_rssi_reported

            val rssi = rssiEvent.rssi
            val hciStatus = rssiEvent.hci_status ?: StatusEnum.STATUS_UNKNOWN
            if (rssi != null && hciStatus == StatusEnum.STATUS_SUCCESS) {
                Reporting.report().distribution(
                    name = BLUETOOTH_DEVICE_RSSI_DISTRIBUTION,
                    aggregations = listOf(MEAN, MIN, MAX),
                ).record(rssi.toDouble(), timestamp = eventTimestampMillis)

                val obfuscatedId = rssiEvent.obfuscated_id?.toByteArray()?.toHexString().orEmpty()
                Reporting.report().event(
                    name = BLUETOOTH_DEVICE_RSSI_DEVICE_ID,
                    latestInReport = true,
                ).add(obfuscatedId)
            }
        }
    }

    override fun atoms(): Set<Int> = setOf(BLUETOOTH_DEVICE_RSSI_REPORTED_ATOM_FIELD_ID)

    companion object {
        private const val BLUETOOTH_DEVICE_RSSI_DISTRIBUTION = "bluetooth.device_rssi"
        private const val BLUETOOTH_DEVICE_RSSI_DEVICE_ID = "bluetooth.device_rssi.device_id"
        private const val BLUETOOTH_DEVICE_RSSI_REPORTED_ATOM_FIELD_ID = 157
    }
}
