package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class LowMemReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(eventTimestampMillis: Long, atom: Atom) {
        if (atom.low_mem_reported != null) {
            Reporting.report().counter(
                name = LOW_MEM_REPORTED_EVENT,
                sumInReport = true,
            ).increment(timestamp = eventTimestampMillis)
        }
    }

    override fun atoms(): Set<Int> = setOf(LOW_MEM_REPORTED_ATOM_ID)

    companion object {
        private const val LOW_MEM_REPORTED_ATOM_ID = 81
        private const val LOW_MEM_REPORTED_EVENT = "memory.low_mem_reported"
    }
}
