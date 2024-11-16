package com.memfault.bort

import android.app.Application
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private const val REQUEST_ID_INPUT_DATA_KEY = "request-id"
private const val TIMEOUT_WORK_TAG = "BUG_REPORT_TIMEOUT"
private const val TIMEOUT_WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.BUG_REPORT_TIMEOUT"

class BugReportRequestTimeoutTask @Inject constructor(
    private val application: Application,
    private val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
) : Task<String?> {
    override fun getMaxAttempts(input: String?) = 1
    override fun convertAndValidateInputData(inputData: Data): String? =
        inputData.getString(REQUEST_ID_INPUT_DATA_KEY)

    override suspend fun doWork(input: String?): TaskResult = TaskResult.SUCCESS.also {
        pendingBugReportRequestAccessor.compareAndSwap(null) {
            if (it == null) {
                false
            } else {
                it.requestId == input
            }
        }.also { (_, request) ->
            request?.broadcastReply(
                application,
                BugReportRequestStatus.ERROR_TIMEOUT,
            )
        }
    }

    companion object {
        val DEFAULT_TIMEOUT = 10.minutes

        fun schedule(
            context: Context,
            requestId: String?,
            existingWorkPolicy: ExistingWorkPolicy,
            duration: Duration = DEFAULT_TIMEOUT,
        ) =
            oneTimeWorkRequest<BugReportRequestTimeoutTask>(
                workDataOf(
                    REQUEST_ID_INPUT_DATA_KEY to requestId,
                ),
            ) {
                addTag(TIMEOUT_WORK_TAG)
                setInitialDelay(duration.toJavaDuration())
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        TIMEOUT_WORK_UNIQUE_NAME_PERIODIC,
                        existingWorkPolicy,
                        workRequest,
                    )
            }

        fun cancel(context: Context) =
            WorkManager.getInstance(context).cancelUniqueWork(TIMEOUT_WORK_UNIQUE_NAME_PERIODIC)
    }
}
