package com.memfault.bort.metrics

import com.memfault.bort.customevent.Reporting
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.shared.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Requests heartbeat report collection, then waits on receiving that report.
 */
class HeartbeatReportCollector {
    private var continuation: Continuation<MetricReport?>? = null

    suspend fun finishAndCollectHeartbeatReport(): MetricReport? = try {
        withTimeout(FINISH_REPORT_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                continuation = cont
                Logger.d("Requesting heartbeat report")
                if (!Reporting.finishHeartbeat()) {
                    Logger.i("Failed to finish heartbeat report!")
                    cont.resume(null)
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        Logger.i("Timed out waiting for heartbeat report")
        null
    } finally {
        continuation = null
    }

    fun handleFinishedHeartbeatReport(report: MetricReport) {
        continuation?.resume(report) ?: Logger.i("Heartbeat report not handled!")
    }

    companion object {
        private val FINISH_REPORT_TIMEOUT = 30.seconds
    }
}
