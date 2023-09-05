package com.memfault.bort.ota.lib

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.DEV_MODE_DISABLED
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * A periodic worker that triggers an update check.
 */
@HiltWorker
class PeriodicSoftwareUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: Updater,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "PeriodicSoftwareUpdateWorker") {
        updater.perform(Action.CheckForUpdate(background = true))
        Result.success()
    }

    companion object {
        fun schedule(
            context: Context,
            settings: SoftwareUpdateSettingsProvider,
        ) {
            Logger.d("schedulePeriodicUpdateCheck: ${settings.get().updateCheckIntervalMs}")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<PeriodicSoftwareUpdateWorker>(
                settings.get().updateCheckIntervalMs, TimeUnit.MILLISECONDS
            ).apply {
                addTag(PERIODIC_UPDATE_WORK)
                setConstraints(constraints)
                setInitialDelay(
                    JitterDelayProvider(
                        jitterDelayConfiguration = { JitterDelayProvider.ApplyJitter.APPLY },
                        devMode = DEV_MODE_DISABLED
                    ).randomJitterDelay()
                )
            }.build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_UPDATE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
