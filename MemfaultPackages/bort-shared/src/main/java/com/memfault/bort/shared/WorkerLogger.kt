package com.memfault.bort.shared

import androidx.work.ListenableWorker.Result
import com.memfault.bort.reporting.Reporting

interface JobReporter {
    /**
     * Job started. Returns an ID which should be used to call [onJobFinished] when the job finishes.
     */
    suspend fun onJobStarted(jobName: String): Long

    /**
     * Job finished (successfully or not). Pass in the [identifier] returned by [onJobStarted].
     */
    suspend fun onJobFinished(
        identifier: Long,
        result: String,
    )

    /**
     * Record a job error. Called in addition to [onJobFinished].
     */
    suspend fun onJobError(jobName: String, e: Throwable)
}

object NoOpJobReporter : JobReporter {
    override suspend fun onJobStarted(jobName: String): Long = 0

    override suspend fun onJobFinished(
        identifier: Long,
        result: String,
    ) = Unit

    override suspend fun onJobError(
        jobName: String,
        e: Throwable,
    ) = Unit
}

/**
 * To be used from [CoroutineWorker] implementations. Does the work, logging any unhandled exceptions as metrics (and
 * rethrowing them).
 */
suspend fun runAndTrackExceptions(
    jobName: String,
    jobReporter: JobReporter,
    doWork: suspend () -> Result,
): Result {
    val id = jobReporter.onJobStarted(jobName)
    return try {
        val result = doWork()
        jobReporter.onJobFinished(id, result.toString())
        result
    } catch (t: Throwable) {
        jobReporter.onJobFinished(id, "Error: ${t.message}")
        jobReporter.onJobError(jobName, t)
        JOB_EXCEPTION_METRIC.add("$jobName: ${t.stackTraceToString().take(500)}")
        throw t
    }
}

private val JOB_EXCEPTION_METRIC = Reporting.report().event(name = "job_error", countInReport = true, internal = true)
