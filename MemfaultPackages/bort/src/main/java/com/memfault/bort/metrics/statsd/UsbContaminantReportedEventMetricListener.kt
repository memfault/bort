package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.UsbContaminantReported
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class UsbContaminantReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.usb_contaminant_reported != null) {
            val contaminantPresent = contaminantAsMetricState(atom.usb_contaminant_reported.status) ?: return
            Reporting.report().boolStateTracker(
                name = USB_CONTAMINANT_REPORTED_EVENT_METRIC_NAME,
                aggregations = listOf(StateAgg.LATEST_VALUE),
            ).state(contaminantPresent, timestamp = eventTimestampMillis)
        }
    }

    private fun contaminantAsMetricState(status: UsbContaminantReported.ContaminantPresenceStatus?): Boolean? =
        when (status) {
            UsbContaminantReported.ContaminantPresenceStatus.CONTAMINANT_STATUS_DETECTED -> true
            UsbContaminantReported.ContaminantPresenceStatus.CONTAMINANT_STATUS_NOT_DETECTED -> false
            else -> null
        }

    override fun atoms(): Set<Int> = setOf(USB_CONTAMINANT_REPORTED_ATOM_ID)

    companion object {
        private const val USB_CONTAMINANT_REPORTED_EVENT_METRIC_NAME = "usb.contaminant_reported"
        private const val USB_CONTAMINANT_REPORTED_ATOM_ID = 146
    }
}
