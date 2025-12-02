package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.AttributionNode
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Listener for BLE scan result received events.
 */
@ContributesMultibinding(scope = SingletonComponent::class)
class BleScanResultReceivedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.ble_scan_result_received != null) {
            val bleScanEvent = atom.ble_scan_result_received

            // Track distribution of number of results per scan
            val numResults = bleScanEvent.num_results ?: 0
            if (numResults > 0) {
                Reporting.report().distribution(
                    name = BLE_SCAN_RESULT_COUNT_DISTRIBUTION,
                    aggregations = listOf(COUNT, MEAN, MAX),
                ).record(numResults.toDouble(), timestamp = eventTimestampMillis)
            }

            // Track event with details
            val attributionInfo = buildAttributionString(bleScanEvent.attribution_node)
            Reporting.report().event(
                name = BLE_SCAN_RESULT_RECEIVED_ATTRIBUTION,
                latestInReport = true,
            ).add(
                value = "numResults=$numResults attribution=$attributionInfo",
                eventTimestampMillis,
            )
        }
    }

    override fun atoms(): Set<Int> = setOf(BLE_SCAN_RESULT_RECEIVED_ATOM_FIELD_ID)

    private fun buildAttributionString(attributionNodes: List<AttributionNode>): String {
        if (attributionNodes.isEmpty()) {
            return "none"
        }
        return attributionNodes.joinToString(" -> ") { node ->
            "uid=${node.uid ?: -1}" + if (!node.tag.isNullOrEmpty()) " tag=${node.tag}" else ""
        }
    }

    companion object {
        private const val BLE_SCAN_RESULT_COUNT_DISTRIBUTION = "bluetooth.ble_scan_result.count"
        private const val BLE_SCAN_RESULT_RECEIVED_ATTRIBUTION = "bluetooth.ble_scan_result.attribution"
        private const val BLE_SCAN_RESULT_RECEIVED_ATOM_FIELD_ID = 4
    }
}
