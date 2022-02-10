package com.memfault.bort.metrics

import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Requests heartbeat report collection, then waits on receiving that report.
 */
@Singleton
class HeartbeatReportCollector @Inject constructor(
    private val settings: SettingsProvider,
) {
    private var continuation: Continuation<MetricReport?>? = null

    suspend fun finishAndCollectHeartbeatReport(): MetricReport? {
        if (!settings.structuredLogSettings.metricsReportEnabled) {
            Logger.d("Metric report disabled: skipping")
            return null
        }

        return try {
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
    }

    fun handleFinishedHeartbeatReport(report: MetricReport) {
        continuation?.resume(report) ?: Logger.i("Heartbeat report not handled!")
    }

    companion object {
        private val FINISH_REPORT_TIMEOUT = 30.seconds
    }
}
