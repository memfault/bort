package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.SlowIo.IoOperation
import com.memfault.bort.metrics.statsd.proto.SlowIo.IoOperation.READ
import com.memfault.bort.metrics.statsd.proto.SlowIo.IoOperation.SYNC
import com.memfault.bort.metrics.statsd.proto.SlowIo.IoOperation.UNMAP
import com.memfault.bort.metrics.statsd.proto.SlowIo.IoOperation.WRITE
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class SlowIoReportedEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(eventTimestampMillis: Long, atom: Atom) {
        if (atom.slow_io != null) {
            val operationStr = operationAsMetricName(atom.slow_io.operation ?: return)
            val count = atom.slow_io.count ?: return

            val metricName = SLOW_IO_REPORTED_EVENT_TEMPLATE.format(operationStr)

            Reporting.report().numberProperty(
                name = metricName,
                addLatestToReport = true,
            ).update(
                value = count,
                timestamp = eventTimestampMillis,
            )
        }
    }

    private fun operationAsMetricName(op: IoOperation): String = when (op) {
        READ -> "read"
        WRITE -> "write"
        UNMAP -> "unmap"
        SYNC -> "sync"
        else -> "unknown"
    }

    override fun atoms(): Set<Int> = setOf(SLOW_IO_ATOM_ID)

    companion object {
        private const val SLOW_IO_ATOM_ID = 92
        private const val SLOW_IO_REPORTED_EVENT_TEMPLATE = "disk.slow_io_%s_24h_count"
    }
}
