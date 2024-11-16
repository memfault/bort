package com.memfault.bort.diagnostics

import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.clientserver.MarBatchingTask
import com.memfault.bort.diagnostics.BortErrorType.JobError
import com.memfault.bort.diagnostics.BortErrors.Companion.STACKTRACE_SIZE
import com.memfault.bort.dropbox.DropBoxGetEntriesTask
import com.memfault.bort.logcat.LogcatCollectionTask
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.requester.BugReportRequestWorker
import com.memfault.bort.settings.SettingsUpdateTask
import com.memfault.bort.shared.JobReporter
import com.memfault.bort.time.AbsoluteTimeProvider
import com.memfault.bort.uploader.FileUploadTask
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class BortJobReporter @Inject constructor(
    private val bortErrorsDb: BortErrorsDb,
    private val bortErrors: BortErrors,
    private val absoluteTimeProvider: AbsoluteTimeProvider,
) : JobReporter {
    override suspend fun onJobStarted(jobName: String): Long {
        bortErrorsDb.dao()
            .deleteJobsEarlierThan(timeMs = absoluteTimeProvider().minus(CLEANUP_AGE).timestamp.toEpochMilli())
        return bortErrorsDb.dao()
            .addJob(DbJob(jobName = jobName, startTimeMs = absoluteTimeProvider().timestamp.toEpochMilli()))
    }

    override suspend fun onJobFinished(
        identifier: Long,
        result: String,
    ) {
        bortErrorsDb.dao()
            .updateJob(id = identifier, endTimeMs = absoluteTimeProvider().timestamp.toEpochMilli(), result = result)
    }

    override suspend fun onJobError(
        jobName: String,
        e: Throwable,
    ) {
        bortErrors.add(JobError, mapOf("job" to jobName, "stacktrace" to e.stackTraceToString().take(STACKTRACE_SIZE)))
    }

    suspend fun getLatestForEachJob(): List<BortJob> = bortErrorsDb.dao().getAllJobsMostRecentFirst().groupBy {
        it.jobName
    }.values.map { it.first() }
        .map { it.asBortJob() }

    suspend fun getIncompleteJobs(): List<BortJob> = bortErrorsDb.dao().getAllJobsMostRecentFirst().filter {
        it.endTimeMs == null
    }.map { it.asBortJob() }

    suspend fun jobStats(): Map<String, String> {
        val allJobs = bortErrorsDb.dao().getAllJobsMostRecentFirst()
        val jobNames = allJobs.map { it.jobName }.toSet() + KNOWN_JOBS.filterNotNull()
        return jobNames.associateWith { jobName ->
            val jobs = allJobs.filter { it.jobName == jobName }.map { it.asBortJob() }
            if (jobs.isNotEmpty()) {
                "ran ${jobs.size} times"
            } else {
                "no record"
            }
        }
    }

    private fun DbJob.asBortJob(): BortJob = BortJob(
        jobName = jobName,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        result = result,
        duration = endTimeMs?.let { (endTimeMs - startTimeMs).milliseconds },
        sinceStart = (absoluteTimeProvider().timestamp.toEpochMilli() - startTimeMs).milliseconds,
    )

    data class BortJob(
        val jobName: String,
        val startTimeMs: Long,
        val endTimeMs: Long?,
        val result: String?,
        val duration: Duration?,
        val sinceStart: Duration,
    )

    companion object {
        private val CLEANUP_AGE = 1.days

        /**
         * List of all the job names known to be reported to [WorkerLogger], so that we can check for any that haven't
         * run recently. Not critical that this is kept up-to-date (or we'd have a better way to ensure that) - but it's
         * nice if it is...
         */
        private val KNOWN_JOBS = setOf(
            FileUploadTask::class.qualifiedName,
            DropBoxGetEntriesTask::class.qualifiedName,
            MetricsCollectionTask::class.qualifiedName,
            BugReportRequestTimeoutTask::class.qualifiedName,
            LogcatCollectionTask::class.qualifiedName,
            SettingsUpdateTask::class.qualifiedName,
            MarBatchingTask::class.qualifiedName,
            BugReportRequestWorker.JOB_NAME,
        )
    }
}
