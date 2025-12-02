package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom

/**
 * A listener for event metrics that are reported by Statsd.
 * [reportEventMetric] will be called for each event metric
 * that is collected by Statsd, it's the responsibility of the
 * listener to handle the event and report it as needed.
 */
interface StatsdEventMetricListener {
    fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    )

    fun atoms(): Set<Int>
}
