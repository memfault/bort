package com.memfault.bort.ota.lib

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

/**
 * A periodic worker that triggers an update check.
 */
class PeriodicSoftwareUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val updater = applicationContext.updater()
        if (updater.updateState.value.allowsUpdateCheck()) {
            updater.perform(Action.CheckForUpdate(background = true))

            // suspend until the check is complete, perform above does not necessarily block depending on
            // the action handler implementation
            updater.updateState.first { it != State.CheckingForUpdates }
        }
        return Result.success()
    }
}
