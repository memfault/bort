package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Listener for Bluetooth Quality Report reported events.
 */
@ContributesMultibinding(scope = SingletonComponent::class)
class BluetoothQualityReportReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.bluetooth_quality_report_reported != null) {
            val qualityReport = atom.bluetooth_quality_report_reported

            val rssi = qualityReport.rssi
            if (rssi != null) {
                Reporting.report().distribution(
                    name = BLUETOOTH_QUALITY_REPORT_RSSI_DISTRIBUTION,
                    aggregations = listOf(MEAN, MIN, MAX),
                ).record(rssi.toDouble(), timestamp = eventTimestampMillis)
            }

            val snr = qualityReport.snr
            if (snr != null) {
                Reporting.report().distribution(
                    name = BLUETOOTH_QUALITY_REPORT_SNR_DISTRIBUTION,
                    aggregations = listOf(MEAN, MIN, MAX),
                ).record(snr.toDouble(), timestamp = eventTimestampMillis)
            }

            val retransmissionCount = qualityReport.retransmission_count
            if (retransmissionCount != null && retransmissionCount > 0) {
                Reporting.report().distribution(
                    name = BLUETOOTH_QUALITY_REPORT_RETRANSMISSION_COUNT_DISTRIBUTION,
                    aggregations = listOf(MEAN, MAX, SUM),
                ).record(retransmissionCount.toDouble(), timestamp = eventTimestampMillis)
            }

            Reporting.report().event(
                name = BLUETOOTH_QUALITY_REPORT_REPORTED_EVENT,
                latestInReport = true,
            ).add(value = qualityReport.toString(), eventTimestampMillis)
        }
    }

    override fun atoms(): Set<Int> = setOf(BLUETOOTH_QUALITY_REPORT_REPORTED_ATOM_FIELD_ID)

    companion object {
        private const val BLUETOOTH_QUALITY_REPORT_RSSI_DISTRIBUTION = "bluetooth.quality_report.rssi"
        private const val BLUETOOTH_QUALITY_REPORT_SNR_DISTRIBUTION = "bluetooth.quality_report.snr"
        private const val BLUETOOTH_QUALITY_REPORT_RETRANSMISSION_COUNT_DISTRIBUTION =
            "bluetooth.quality_report.retransmission_count"
        private const val BLUETOOTH_QUALITY_REPORT_REPORTED_EVENT = "bluetooth.quality_report.reported"
        private const val BLUETOOTH_QUALITY_REPORT_REPORTED_ATOM_FIELD_ID = 161
    }
}
