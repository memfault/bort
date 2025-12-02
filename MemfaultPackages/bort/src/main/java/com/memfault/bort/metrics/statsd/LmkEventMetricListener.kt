package com.memfault.bort.metrics.statsd

import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.LmkKillOccurred
import com.memfault.bort.reporting.Reporting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class LmkEventMetricListener @Inject constructor() : StatsdEventMetricListener {
    override fun reportEventMetric(
        eventTimestampMillis: Long,
        atom: Atom,
    ) {
        if (atom.lmk_kill_kill_occurred != null) {
            val lmkEvent = atom.lmk_kill_kill_occurred

            Reporting.report().event(
                name = LMK_KILL_OCCURRED_EVENT,
                countInReport = true,
                latestInReport = true,
            ).add(
                value = "uid=${lmkEvent.uid} " +
                    "processName=${lmkEvent.process_name} " +
                    "rssInBytes=${lmkEvent.rss_in_bytes} " +
                    "cacheInBytes=${lmkEvent.cache_in_bytes} " +
                    "swapInBytes=${lmkEvent.swap_in_bytes} " +
                    "freeMemKb=${lmkEvent.free_mem_kb} " +
                    "freeSwapKb=${lmkEvent.free_swap_kb} " +
                    "maxTrashing=${lmkEvent.max_thrashing} " +
                    "reason=\"${humanReadableLmkReason(lmkEvent.reason ?: LmkKillOccurred.Reason.UNKNOWN)}\"",
                eventTimestampMillis,
            )
        }
    }

    override fun atoms(): Set<Int> = setOf(LMK_KILL_OCCURRED_ATOM_FIELD_ID)

    private fun humanReadableLmkReason(reason: LmkKillOccurred.Reason) =
        @Suppress("UNUSED_EXPRESSION")
        when (reason) {
            LmkKillOccurred.Reason.UNKNOWN -> "unknown [0]"
            LmkKillOccurred.Reason.PRESSURE_AFTER_KILL -> "memory pressure after kill"
            LmkKillOccurred.Reason.NOT_RESPONDING -> "not responding"
            LmkKillOccurred.Reason.LOW_SWAP_AND_THRASHING -> "low swap and trashing"
            LmkKillOccurred.Reason.LOW_MEM_AND_SWAP -> "low memory and swap"
            LmkKillOccurred.Reason.LOW_MEM_AND_THRASHING -> "low memory and trashing"
            LmkKillOccurred.Reason.DIRECT_RECL_AND_THRASHING -> "direct reclaim and trashing"
            LmkKillOccurred.Reason.LOW_MEM_AND_SWAP_UTIL -> "low memory and swap utilization"
            LmkKillOccurred.Reason.LOW_FILECACHE_AFTER_THRASHING -> "file cache too low after trashing"
            LmkKillOccurred.Reason.LOW_MEM -> "low memory"
            LmkKillOccurred.Reason.DIRECT_RECL_STUCK -> "direct reclaim stuck"
        }

    companion object {
        private const val LMK_KILL_OCCURRED_EVENT = "memory.lmk_kill_occurred"
        private const val LMK_KILL_OCCURRED_ATOM_FIELD_ID = 51
    }
}
