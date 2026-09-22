package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class UsbComplianceWarningsReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        eventElapsedRealtimeMillis: Long,
        atom: Atom,
    ) {
        if (atom.usb_compliance_warnings_reported != null) {
            val warnings = atom.usb_compliance_warnings_reported.compliance_warnings
            if (warnings.isEmpty()) return

            Reporting.report().counter(
                name = USB_COMPLIANCE_WARNINGS_COUNT,
            ).incrementBy(
                by = warnings.size,
                timestamp = eventTimestampMillis,
                uptime = eventElapsedRealtimeMillis,
            )

            Reporting.report().event(
                name = USB_COMPLIANCE_WARNINGS_EVENT,
                latestInReport = true,
            ).add(
                value = warnings.joinToString(separator = ",") { it.name },
                timestamp = eventTimestampMillis,
                uptime = eventElapsedRealtimeMillis,
            )
        }
    }

    override fun atoms(): Set<Int> = setOf(USB_COMPLIANCE_WARNINGS_REPORTED_ATOM_ID)

    companion object {
        internal const val USB_COMPLIANCE_WARNINGS_COUNT = "usb.compliance_warnings"
        internal const val USB_COMPLIANCE_WARNINGS_EVENT = "usb.compliance_warning"
        private const val USB_COMPLIANCE_WARNINGS_REPORTED_ATOM_ID = 582
    }
}
