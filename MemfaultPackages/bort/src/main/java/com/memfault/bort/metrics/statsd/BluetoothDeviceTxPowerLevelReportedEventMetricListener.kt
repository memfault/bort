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
 * Listener for Bluetooth device TX power level reported events.
 */
@OptIn(ExperimentalStdlibApi::class)
@ContributesMultibinding(scope = SingletonComponent::class)
class BluetoothDeviceTxPowerLevelReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.bluetooth_device_tx_power_level_reported != null) {
            val txPowerEvent = atom.bluetooth_device_tx_power_level_reported

            // Track TX power level distribution (power consumption analysis)
            val txPowerLevel = txPowerEvent.transmit_power_level
            val hciStatus = txPowerEvent.hci_status ?: StatusEnum.STATUS_UNKNOWN
            if (txPowerLevel != null && hciStatus == StatusEnum.STATUS_SUCCESS) {
                Reporting.report().distribution(
                    name = BLUETOOTH_DEVICE_TX_POWER_LEVEL_DISTRIBUTION,
                    aggregations = listOf(MEAN, MIN, MAX),
                ).record(txPowerLevel.toDouble(), timestamp = eventTimestampMillis)

                val obfuscatedId = txPowerEvent.obfuscated_id?.toByteArray()?.toHexString().orEmpty()
                Reporting.report().event(
                    name = BLUETOOTH_DEVICE_TX_POWER_LEVEL_DEVICE_ID,
                    latestInReport = true,
                ).add(obfuscatedId)
            }
        }
    }

    override fun atoms(): Set<Int> = setOf(BLUETOOTH_DEVICE_TX_POWER_LEVEL_REPORTED_ATOM_FIELD_ID)

    companion object {
        private const val BLUETOOTH_DEVICE_TX_POWER_LEVEL_DISTRIBUTION = "bluetooth.device_tx_power_level"
        private const val BLUETOOTH_DEVICE_TX_POWER_LEVEL_DEVICE_ID = "bluetooth.device_tx_power_level.device_id"
        private const val BLUETOOTH_DEVICE_TX_POWER_LEVEL_REPORTED_ATOM_FIELD_ID = 159
    }
}
