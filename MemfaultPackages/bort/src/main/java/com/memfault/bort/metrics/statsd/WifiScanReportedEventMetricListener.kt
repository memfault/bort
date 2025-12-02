package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class WifiScanReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(eventTimestampMillis: Long, atom: Atom) {
        if (atom.wifi_scan_reported != null) {
            val foundNetworks = atom.wifi_scan_reported.count_networks_found ?: return
            Reporting.report()
                .distribution(
                    name = METRIC_NAME,
                    aggregations = listOf(MEAN, COUNT, LATEST_VALUE),
                ).record(value = foundNetworks.toLong(), timestamp = eventTimestampMillis)
        }
    }

    override fun atoms(): Set<Int> = setOf(WIFI_SCAN_REPORTED_ATOM_ID)

    private companion object {
        const val METRIC_NAME = "connectivity.wifi.scan_network_count"
        const val WIFI_SCAN_REPORTED_ATOM_ID = 325
    }
}
