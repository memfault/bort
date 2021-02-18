package com.memfault.bort.settings

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.shared.Logger

private const val PERIODIC_REQUESTER_RESTART_TASK_TAG = "PERIODIC_REQUESTER_RESTART_TASK"

class PeriodicRequesterRestartTask(
    override val getMaxAttempts: () -> Int,
    private val periodicWorkRequesters: List<PeriodicWorkRequester>,
) : Task<Unit>() {
    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        periodicWorkRequesters.forEach {
            it.evaluateSettingsChange()
        }
        Logger.test("Periodic tasks were restarted")
        return TaskResult.SUCCESS
    }

    override fun convertAndValidateInputData(inputData: Data) {
    }

    companion object {
        fun schedule(context: Context) =
            oneTimeWorkRequest<PeriodicRequesterRestartTask>(workDataOf()) {
                addTag(PERIODIC_REQUESTER_RESTART_TASK_TAG)
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        PERIODIC_REQUESTER_RESTART_TASK_TAG,
                        ExistingWorkPolicy.KEEP,
                        workRequest,
                    )
            }
    }
}
