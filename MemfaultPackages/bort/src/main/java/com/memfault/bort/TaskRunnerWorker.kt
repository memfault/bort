package com.memfault.bort

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.memfault.bort.clientserver.MarBatchingTask
import com.memfault.bort.diagnostics.BortJobReporter
import com.memfault.bort.dropbox.DropBoxGetEntriesTask
import com.memfault.bort.logcat.LogcatCollectionTask
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.MAX_ATTEMPTS
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.settings.SettingsUpdateTask
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import com.memfault.bort.uploader.FileUploadTask
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Helpers around androidx.work that delegate the actual work to a Task, with
 * the goal of facilitating unit testing by minimizing the dependencies on Android APIs.
 */

enum class TaskResult {
    SUCCESS,
    FAILURE,
    RETRY,
    ;

    fun toWorkerResult() =
        when (this) {
            SUCCESS -> Result.success()
            FAILURE -> Result.failure()
            RETRY -> Result.retry()
        }
}

interface Task<I> {
    suspend fun doWork(input: I): TaskResult

    fun finally(input: I?) = Unit

    fun getMaxAttempts(input: I): Int

    fun convertAndValidateInputData(inputData: Data): I
}

inline fun <reified K : Task<*>> enqueueWorkOnce(
    context: Context,
    inputData: Data = Data.EMPTY,
    block: OneTimeWorkRequest.Builder.() -> Unit = {},
): WorkRequest =
    oneTimeWorkRequest<K>(inputData, block).also {
        WorkManager.getInstance(context)
            .enqueue(it)
    }

inline fun <reified K : Task<*>> oneTimeWorkRequest(
    inputData: Data,
    block: OneTimeWorkRequest.Builder.() -> Unit = {},
): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<TaskRunnerWorker>()
        .setInputData(
            addWorkDelegateClass(
                checkNotNull(K::class.qualifiedName),
                inputData,
            ),
        )
        .apply(block)
        .build()

inline fun <reified K : Task<*>> periodicWorkRequest(
    repeatInterval: Duration,
    inputData: Data,
    block: PeriodicWorkRequest.Builder.() -> Unit = {},
): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<TaskRunnerWorker>(
        repeatInterval.toDouble(DurationUnit.MILLISECONDS).toLong(),
        TimeUnit.MILLISECONDS,
    )
        .setInputData(
            addWorkDelegateClass(
                checkNotNull(K::class.qualifiedName),
                inputData,
            ),
        )
        .apply(block)
        .build()

@HiltWorker
open class TaskRunnerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileUpload: Provider<FileUploadTask>,
    private val dropBox: Provider<DropBoxGetEntriesTask>,
    private val metrics: Provider<MetricsCollectionTask>,
    private val bugReportTimeout: Provider<BugReportRequestTimeoutTask>,
    private val logcat: Provider<LogcatCollectionTask>,
    private val settings: Provider<SettingsUpdateTask>,
    private val marBatching: Provider<MarBatchingTask>,
    private val bortJobReporter: BortJobReporter,
    private val builtInMetricsStore: BuiltinMetricsStore,
) : CoroutineWorker(appContext, params) {

    open fun createTask(inputData: Data): Task<*>? = when (inputData.workDelegateClass) {
        FileUploadTask::class.qualifiedName -> fileUpload.get()
        DropBoxGetEntriesTask::class.qualifiedName -> dropBox.get()
        MetricsCollectionTask::class.qualifiedName -> metrics.get()
        BugReportRequestTimeoutTask::class.qualifiedName -> bugReportTimeout.get()
        LogcatCollectionTask::class.qualifiedName -> logcat.get()
        SettingsUpdateTask::class.qualifiedName -> settings.get()
        MarBatchingTask::class.qualifiedName -> marBatching.get()
        else -> null
    }

    override suspend fun doWork(): Result =
        runAndTrackExceptions(jobName = inputData.workDelegateClass, bortJobReporter) {
            when (val task = createTask(inputData)) {
                null -> Result.failure().also {
                    Logger.e("Could not create task for inputData (id=$id)")
                }

                else -> doWork(task).toWorkerResult()
            }
        }

    private suspend fun <T> doWork(task: Task<T>): TaskResult {
        val input = try {
            task.convertAndValidateInputData(inputData)
        } catch (e: Exception) {
            return TaskResult.FAILURE.also {
                Logger.e("Could not deserialize input data (id=$id)", e)
                task.finally(null)
            }
        }

        val maxAttempts = task.getMaxAttempts(input)
        return if (runAttemptCount > maxAttempts) {
            Logger.e("Reached max attempts ($maxAttempts) for job $id with tags $tags")
            builtInMetricsStore.increment("${MAX_ATTEMPTS}_$tags")
            task.finally(input)
            TaskResult.FAILURE
        } else {
            task.doWork(input)
                .also { result ->
                    if (result != TaskResult.RETRY) {
                        task.finally(input)
                    }
                }
        }
    }
}

private const val WORK_DELEGATE_CLASS = "__WORK_DELEGATE_CLASS"

val Data.workDelegateClass: String
    get() = getString(WORK_DELEGATE_CLASS) ?: "unknown"

fun addWorkDelegateClass(
    className: String,
    inputData: Data,
): Data =
    Data.Builder()
        .putAll(mapOf(WORK_DELEGATE_CLASS to className))
        .putAll(inputData)
        .build()
