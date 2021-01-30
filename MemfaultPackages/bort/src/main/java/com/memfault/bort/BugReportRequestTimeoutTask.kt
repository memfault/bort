package com.memfault.bort

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlin.time.Duration
import kotlin.time.minutes
import kotlin.time.toJavaDuration

private const val REQUEST_ID_INPUT_DATA_KEY = "request-id"
private const val TIMEOUT_WORK_TAG = "BUG_REPORT_TIMEOUT"
private const val TIMEOUT_WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.BUG_REPORT_TIMEOUT"

class BugReportRequestTimeoutTask(
    private val context: Context,
    private val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
) : Task<String?>() {
    override val maxAttempts: Int = 1
    override fun convertAndValidateInputData(inputData: Data): String? =
        inputData.getString(REQUEST_ID_INPUT_DATA_KEY)

    override suspend fun doWork(worker: TaskRunnerWorker, input: String?): TaskResult = doWork(input)

    fun doWork(requestId: String?) = TaskResult.SUCCESS.also {
        pendingBugReportRequestAccessor.compareAndSwap(null) {
            if (it == null) false
            else it.requestId == requestId
        }.also { (_, request) ->
            request?.broadcastReply(
                context, BugReportRequestStatus.ERROR_TIMEOUT
            )
        }
    }

    companion object {
        val DEFAULT_TIMEOUT = 10.minutes

        fun schedule(
            context: Context,
            requestId: String?,
            existingWorkPolicy: ExistingWorkPolicy,
            duration: Duration = DEFAULT_TIMEOUT
        ) =
            oneTimeWorkRequest<BugReportRequestTimeoutTask>(
                workDataOf(
                    REQUEST_ID_INPUT_DATA_KEY to requestId,
                )
            ) {
                addTag(TIMEOUT_WORK_TAG)
                setInitialDelay(duration.toJavaDuration())
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        TIMEOUT_WORK_UNIQUE_NAME_PERIODIC,
                        existingWorkPolicy,
                        workRequest
                    )
            }

        fun cancel(context: Context) =
            WorkManager.getInstance(context).cancelUniqueWork(TIMEOUT_WORK_UNIQUE_NAME_PERIODIC)
    }
}
