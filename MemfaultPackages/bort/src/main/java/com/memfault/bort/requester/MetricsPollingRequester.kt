package com.memfault.bort.requester

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskResult.SUCCESS
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.dropbox.DropboxRequester
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * This is a bit different from our other requesters - it exists only to wake bort every 15 minutes (minimum
 * allowed by WorkManager), to ensure that the metrics polling task is always being scheduled (see [MetricsPoller]).
 */
@ContributesMultibinding(SingletonComponent::class)
class MetricsPollingRequester @Inject constructor(
    private val application: Application,
    private val logcatCollectionRequester: LogcatCollectionRequester,
    private val dropboxRequester: DropboxRequester,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(
        justBooted: Boolean,
        settingsChanged: Boolean,
    ) {
        periodicWorkRequest<MetricsPollingWakeTask>(
            WAKE_INTERVAL,
            workDataOf(),
        ) {
            addTag(WORK_TAG_PERIODIC)
        }.also { workRequest ->
            WorkManager.getInstance(application)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest,
                )
        }
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(application).cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        // This task only needs to run if there isn't already another 15-minute periodic scheduled task.
        val dropboxPollingRunningRegularly =
            dropboxRequester.isScheduledAt(settings).let { it != null && it <= WAKE_INTERVAL }
        val periodicLogcatRunningRegularly =
            logcatCollectionRequester.isScheduledAt(settings).let { it != null && it <= WAKE_INTERVAL }
        if (dropboxPollingRunningRegularly || periodicLogcatRunningRegularly) {
            return false
        }
        return settings.metricsSettings.pollingInterval.isPositive()
    }

    override suspend fun diagnostics(): BortWorkInfo {
        return WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkFlow(WORK_UNIQUE_NAME_PERIODIC)
            .asBortWorkInfo("metrics_polling")
    }

    override suspend fun parametersChanged(
        old: SettingsProvider,
        new: SettingsProvider,
    ): Boolean {
        return old.metricsSettings.pollingInterval != new.metricsSettings.pollingInterval
    }

    companion object {
        private const val WORK_TAG_PERIODIC = "METRICS_POLLING_WAKE"
        private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.METRICS_POLL_WAKE"
        private val WAKE_INTERVAL = 15.minutes
    }
}

class MetricsPollingWakeTask @Inject constructor(
    override val getMaxAttempts: () -> Int,
    override val metrics: BuiltinMetricsStore,
) : Task<Unit>() {
    override suspend fun doWork(
        worker: TaskRunnerWorker,
        input: Unit,
    ): TaskResult {
        Logger.d("MetricsPollingWakeTask")
        return SUCCESS
    }

    override fun convertAndValidateInputData(inputData: Data) {
    }
}
