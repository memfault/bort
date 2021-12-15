package com.memfault.bort

import android.content.Context
import android.os.SystemClock
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.hours
import kotlin.time.milliseconds
import kotlin.time.toJavaDuration

private const val TIMEOUT_WORK_TAG = "FILE_UPLOAD_HOLDING_AREA_TIMEOUT"
private const val TIMEOUT_WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.FILE_UPLOAD_HOLDING_AREA_TIMEOUT"

class FileUploadHoldingAreaTimeoutTask @Inject constructor(
    private val fileUploadHoldingArea: FileUploadHoldingArea,
    override val metrics: BuiltinMetricsStore,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data): Unit = Unit

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult = doWork()

    fun doWork() = TaskResult.SUCCESS.also {
        fileUploadHoldingArea.handleTimeout(SystemClock.elapsedRealtime().milliseconds)
    }

    companion object {
        fun reschedule(
            context: Context,
            duration: Duration = 12.hours
        ) =
            oneTimeWorkRequest<BugReportRequestTimeoutTask>(workDataOf()) {
                addTag(TIMEOUT_WORK_TAG)
                setInitialDelay(duration.toJavaDuration())
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        TIMEOUT_WORK_UNIQUE_NAME_PERIODIC,
                        // Every time reschedule() is called, the timeout is reset
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
            }
    }
}

@ContributesBinding(SingletonComponent::class)
class RealFileUploadHoldingSchedule @Inject constructor(
    private val context: Context,
) : FileUploadHoldingReschedule {
    override fun invoke() {
        FileUploadHoldingAreaTimeoutTask.reschedule(context)
    }
}

fun interface FileUploadHoldingReschedule : () -> Unit
