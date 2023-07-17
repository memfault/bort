package com.memfault.bort.metrics

import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.dropbox.MetricReportWithHighResFile
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.StructuredLogSettings
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Requests heartbeat report collection, then waits on receiving that report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HeartbeatReportCollector @Inject constructor(
    private val settings: StructuredLogSettings,
    private val reportFinisher: ReportFinisher,
) {
    private var receivedReport: CompletableDeferred<MetricReport>? = null
    private var receivedHighResFile: CompletableDeferred<File>? = null
    private val mutex = Mutex()

    suspend fun finishAndCollectHeartbeatReport(
        timeout: Duration = FINISH_REPORT_TIMEOUT,
    ): MetricReportWithHighResFile? = try {
        // Lock report collecting: this will throw an IllegalStateException if already locked.
        mutex.withLock(owner = "collector") {
            finishAndCollectHeartbeatReportLocked(timeout)
        }
    } catch (e: IllegalStateException) {
        Logger.w("Couldn't get HeartbeatReportCollector lock!")
        null
    }

    private suspend fun finishAndCollectHeartbeatReportLocked(timeout: Duration): MetricReportWithHighResFile? {
        if (!settings.metricsReportEnabled) {
            Logger.d("Metric report disabled: skipping")
            return null
        }
        // Note: this doesn't mean the service definitely supports high res metrics; we would time out waiting for the
        // high res file if it is enabled but not supported.
        val highResMetricsEnabled = settings.highResMetricsEnabled

        try {
            receivedReport = CompletableDeferred()
            receivedHighResFile = CompletableDeferred()

            if (!reportFinisher.finishHeartbeat()) {
                Logger.i("Failed to finish heartbeat report!")
                return null
            }

            val start = Instant.now()
            val report = withTimeoutOrNull(timeout) { receivedReport?.await() }
            if (report == null) {
                Logger.i("Timed out waiting for metric report")
                // If we got a high res file but not a report for some reason, delete it.
                try {
                    receivedHighResFile?.getCompleted().let { it?.deleteSilently() }
                } catch (e: IllegalStateException) {
                }
                return null
            }
            val elapsed = java.time.Duration.between(start, Instant.now()).toKotlinDuration()
            // It would be simpler to use one single withTimeout call, but we could not easily use the metricReport if
            // the highResFile timed-out.
            val remainingTimeout = timeout - elapsed
            val highResFile = if (highResMetricsEnabled) {
                withTimeoutOrNull(remainingTimeout) { receivedHighResFile?.await() }
            } else null
            return MetricReportWithHighResFile(report, highResFile)
        } finally {
            receivedReport = null
            receivedHighResFile = null
        }
    }

    fun handleFinishedHeartbeatReport(report: MetricReport) {
        if (receivedReport?.complete(report) != true) {
            Logger.i("Heartbeat report not handled!")
        }
    }

    fun handleHighResMetricsFile(file: File) {
        if (receivedHighResFile?.complete(file) != true) {
            Logger.i("High-res metrics file not handled! Deleting..")
            file.deleteSilently()
        }
    }

    companion object {
        private val FINISH_REPORT_TIMEOUT = 30.seconds
    }
}

interface ReportFinisher {
    fun finishHeartbeat(): Boolean
}

@ContributesBinding(scope = SingletonComponent::class)
class RealReportFinisher @Inject constructor() : ReportFinisher {
    override fun finishHeartbeat(): Boolean = Reporting.finishHeartbeat()
}
