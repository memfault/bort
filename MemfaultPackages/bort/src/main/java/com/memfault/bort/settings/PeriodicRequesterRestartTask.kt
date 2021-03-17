package com.memfault.bort.settings

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortJson
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.shared.Logger

private const val PERIODIC_REQUESTER_RESTART_TASK_TAG = "PERIODIC_REQUESTER_RESTART_TASK"
private const val FETCHED_SETTINGS_UPDATE_KEY = "update"

class PeriodicRequesterRestartTask(
    override val getMaxAttempts: () -> Int,
    private val periodicWorkRequesters: List<PeriodicWorkRequester>,
) : Task<FetchedSettingsUpdate>() {
    override suspend fun doWork(worker: TaskRunnerWorker, input: FetchedSettingsUpdate): TaskResult {
        val old = DynamicSettingsProvider(object : ReadonlyFetchedSettingsProvider {
            override fun get() = input.old
        })
        val new = DynamicSettingsProvider(object : ReadonlyFetchedSettingsProvider {
            override fun get() = input.new
        })
        periodicWorkRequesters.forEach {
            it.evaluateSettingsChange(old, new)
        }
        Logger.test("Periodic tasks were restarted")
        return TaskResult.SUCCESS
    }

    override fun convertAndValidateInputData(inputData: Data): FetchedSettingsUpdate =
        BortJson.decodeFromString(
            FetchedSettingsUpdate.serializer(),
            checkNotNull(inputData.getString(FETCHED_SETTINGS_UPDATE_KEY))
        )

    companion object {
        fun schedule(context: Context, fetchedSettingsUpdate: FetchedSettingsUpdate) =
            oneTimeWorkRequest<PeriodicRequesterRestartTask>(
                workDataOf(
                    FETCHED_SETTINGS_UPDATE_KEY to BortJson.encodeToString(
                        FetchedSettingsUpdate.serializer(),
                        fetchedSettingsUpdate,
                    )
                )
            ) {
                addTag(PERIODIC_REQUESTER_RESTART_TASK_TAG)
            }.also { workRequest ->
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        PERIODIC_REQUESTER_RESTART_TASK_TAG,
                        ExistingWorkPolicy.REPLACE,
                        workRequest,
                    )
            }
    }
}
