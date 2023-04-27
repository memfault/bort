package com.memfault.bort.shared

import androidx.work.ListenableWorker.Result
import com.memfault.bort.reporting.Reporting

/**
 * To be used from [CoroutineWorker] implementations. Does the work, logging any unhandled exceptions as metrics (and
 * rethrowing them).
 */
suspend fun runAndTrackExceptions(jobName: String?, doWork: suspend () -> Result): Result = try {
    doWork()
} catch (t: Throwable) {
    JOB_EXCEPTION_METRIC.add("$jobName: ${t.stackTraceToString().take(500)}")
    throw t
}

private val JOB_EXCEPTION_METRIC = Reporting.report().event(name = "job_error", countInReport = true, internal = true)
