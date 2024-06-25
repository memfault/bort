package com.memfault.bort.metrics

import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.time.CombinedTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests heartbeat report collection, then waits on receiving that report.
 */
@Singleton
class HeartbeatReportCollector @Inject constructor(
    private val customMetrics: CustomMetrics,
) {
    suspend fun finishAndCollectHeartbeatReport(now: CombinedTime): CustomReport =
        customMetrics.collectHeartbeat(endTimestampMs = now.timestamp.toEpochMilli())
}
