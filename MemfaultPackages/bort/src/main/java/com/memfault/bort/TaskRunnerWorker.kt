package com.memfault.bort

import android.content.Context
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
import com.memfault.bort.shared.Logger
import com.memfault.bort.uploader.limitAttempts
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Helpers around androidx.work that delegate the actual work to a Task, with
 * the goal of facilitating unit testing by minimizing the dependencies on Android APIs.
 */

enum class TaskResult {
    SUCCESS,
    FAILURE,
    RETRY;

    fun toWorkerResult() =
        when (this) {
            SUCCESS -> Result.success()
            FAILURE -> Result.failure()
            RETRY -> Result.retry()
        }
}

abstract class Task<I> {
    abstract val getMaxAttempts: () -> Int

    suspend fun doWork(worker: TaskRunnerWorker): TaskResult {
        val input = try {
            convertAndValidateInputData(worker.inputData)
        } catch (e: Exception) {
            return TaskResult.FAILURE.also {
                Logger.e("Could not deserialize input data (id=${worker.id})", e)
                finally(null)
            }
        }
        return worker.limitAttempts(getMaxAttempts(input), { finally(input) }) {
            doWork(worker, input)
        }
    }

    abstract suspend fun doWork(worker: TaskRunnerWorker, input: I): TaskResult

    open fun finally(input: I?) {}

    open fun getMaxAttempts(input: I): Int = getMaxAttempts()

    abstract fun convertAndValidateInputData(inputData: Data): I
}

inline fun <reified K : Task<*>> enqueueWorkOnce(
    context: Context,
    inputData: Data = Data.EMPTY,
    block: OneTimeWorkRequest.Builder.() -> Unit = {}
): WorkRequest =
    oneTimeWorkRequest<K>(inputData, block).also {
        WorkManager.getInstance(context)
            .enqueue(it)
    }

inline fun <reified K : Task<*>> oneTimeWorkRequest(
    inputData: Data,
    block: OneTimeWorkRequest.Builder.() -> Unit = {}
): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<TaskRunnerWorker>()
        .setInputData(
            addWorkDelegateClass(
                checkNotNull(K::class.qualifiedName), inputData
            )
        )
        .apply(block)
        .build()

inline fun <reified K : Task<*>> periodicWorkRequest(
    repeatInterval: Duration,
    inputData: Data,
    block: PeriodicWorkRequest.Builder.() -> Unit = {}
): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<TaskRunnerWorker>(
        repeatInterval.inMilliseconds.toLong(),
        TimeUnit.MILLISECONDS,
    )
        .setInputData(
            addWorkDelegateClass(
                checkNotNull(K::class.qualifiedName), inputData
            )
        )
        .apply(block)
        .build()

interface TaskFactory {
    fun create(inputData: Data): Task<*>?
}

class TaskRunnerWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val taskFactory: TaskFactory
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result =
        when (val task = taskFactory.create(inputData)) {
            null -> Result.failure().also {
                Logger.e("Could not create task for inputData (id=$id)")
            }
            else -> task.doWork(this).toWorkerResult()
        }
}

private const val WORK_DELEGATE_CLASS = "__WORK_DELEGATE_CLASS"

val Data.workDelegateClass: String?
    get() = getString(WORK_DELEGATE_CLASS)

fun addWorkDelegateClass(className: String, inputData: Data): Data =
    Data.Builder()
        .putAll(mapOf(WORK_DELEGATE_CLASS to className))
        .putAll(inputData)
        .build()
