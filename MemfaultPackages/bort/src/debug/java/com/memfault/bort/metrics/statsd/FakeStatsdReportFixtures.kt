package com.memfault.bort.metrics.statsd

import android.os.SystemClock
import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.AttributionNode
import com.memfault.bort.metrics.statsd.proto.BleScanResultReceived
import com.memfault.bort.metrics.statsd.proto.BluetoothDeviceRssiReported
import com.memfault.bort.metrics.statsd.proto.BluetoothDeviceTxPowerLevelReported
import com.memfault.bort.metrics.statsd.proto.BluetoothQualityReportReported
import com.memfault.bort.metrics.statsd.proto.BqrIdEnum.BQR_ID_UNKNOWN
import com.memfault.bort.metrics.statsd.proto.BqrPacketTypeEnum.BQR_PACKET_TYPE_UNKNOWN
import com.memfault.bort.metrics.statsd.proto.ConfigMetricsReport
import com.memfault.bort.metrics.statsd.proto.EventMetricData
import com.memfault.bort.metrics.statsd.proto.LowMemReported
import com.memfault.bort.metrics.statsd.proto.SlowIo
import com.memfault.bort.metrics.statsd.proto.StatsLogReport
import com.memfault.bort.metrics.statsd.proto.StatsLogReport.EventMetricDataWrapper
import com.memfault.bort.metrics.statsd.proto.StatusEnum
import com.memfault.bort.metrics.statsd.proto.StatusEnum.STATUS_SUCCESS
import com.memfault.bort.metrics.statsd.proto.UsbContaminantReported
import com.memfault.bort.metrics.statsd.proto.WifiDisconnectReported
import com.memfault.bort.metrics.statsd.proto.WifiDisconnectReported.FailureCode
import com.memfault.bort.metrics.statsd.proto.WifiScanReported

/**
 * Mock fixtures of statsd metrics for testing purposes.
 * Statsd metrics that cannot be easily generated in tests can be mocked
 * here.
 */
object FakeStatsdReportFixtures {
    fun mockReport(): ConfigMetricsReport =
        ConfigMetricsReport(
            listOf(
                StatsLogReport(
                    event_metrics = EventMetricDataWrapper(
                        data_ = buildList {
                            add(bleScanResultReceived())
                            add(bluetoothDeviceRssiReported())
                            add(bluetoothDeviceTxPowerLevelReported())
                            add(bluetoothQualityReportReported())
                            add(wifiDisconnected())
                            add(wifiScanReported())
                            add(lowMemReported())
                            addAll(slowIo())
                            add(usbContaminantDetected())
                        }.map { it.toEventMetricData() },
                    ),
                ),
            ),
        )

    private fun bleScanResultReceived(): Atom = Atom(
        ble_scan_result_received = BleScanResultReceived(
            attribution_node = listOf(
                AttributionNode(uid = 1000, tag = "system"),
            ),
            num_results = 3,
        ),
    )

    private fun bluetoothDeviceRssiReported(): Atom = Atom(
        bluetooth_device_rssi_reported = BluetoothDeviceRssiReported(
            obfuscated_id = null,
            connection_handle = 0xFFFF,
            hci_status = StatusEnum.STATUS_SUCCESS,
            rssi = -44,
            metric_id = 0,
        ),
    )

    private fun bluetoothDeviceTxPowerLevelReported(): Atom = Atom(
        bluetooth_device_tx_power_level_reported = BluetoothDeviceTxPowerLevelReported(
            obfuscated_id = null,
            connection_handle = 0xFFFF,
            hci_status = STATUS_SUCCESS,
            transmit_power_level = 5,
            metric_id = 0,
        ),
    )

    private fun bluetoothQualityReportReported(): Atom = Atom(
        bluetooth_quality_report_reported = BluetoothQualityReportReported(
            quality_report_id = BQR_ID_UNKNOWN,
            packet_types = BQR_PACKET_TYPE_UNKNOWN,
            connection_handle = 0xFFFF,
            connection_role = 0,
            tx_power_level = 6,
            rssi = -45,
            snr = 25,
            retransmission_count = 10,
        ),
    )

    private fun wifiDisconnected(): Atom = Atom(
        wifi_disconnect_reported = WifiDisconnectReported(
            connected_duration_seconds = 120,
            failure_code = FailureCode.WIFI_DISABLED,
            last_rssi = -70,
            last_link_speed = 54,
        ),
    )

    private fun wifiScanReported(): Atom = Atom(
        wifi_scan_reported = WifiScanReported(
            count_networks_found = 5,
            scan_duration_millis = 200,
        ),
    )

    private fun lowMemReported(): Atom = Atom(
        low_mem_reported = LowMemReported(),
    )

    private fun slowIo(): List<Atom> = SlowIo.IoOperation.entries.map { op ->
        Atom(
            slow_io = SlowIo(
                operation = op,
                count = 42,
            ),
        )
    }

    private fun usbContaminantDetected(): Atom = Atom(
        usb_contaminant_reported = UsbContaminantReported(
            id = "0001",
            status = UsbContaminantReported.ContaminantPresenceStatus.CONTAMINANT_STATUS_DETECTED,
        ),
    )
}

private fun Atom.toEventMetricData(): EventMetricData = EventMetricData(
    elapsed_timestamp_nanos = SystemClock.elapsedRealtimeNanos(),
    atom = this,
)
