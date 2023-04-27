package com.memfault.bort.requester

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskResult.SUCCESS
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.settings.SettingsProvider
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

class UptimeTickTask @Inject constructor(
    override val metrics: BuiltinMetricsStore,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult = SUCCESS

    override fun convertAndValidateInputData(inputData: Data) = Unit
}

/**
 * Regularly triggers the bort process to start, so that we keep tracking uptime on older
 * Bort SDK versions (where we are missing most of the regular tasks which would ordinarily do
 * this).
 */
@ContributesMultibinding(SingletonComponent::class)
class UptimeTickRequester @Inject constructor(
    private val context: Context,
    private val bortSystemCapabilities: BortSystemCapabilities,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        periodicWorkRequest<UptimeTickTask>(
            INTERVAL,
            workDataOf()
        ) {
            addTag(UPTIME_WORK_TAG)
        }.also { workRequest ->
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UPTIME_UNIQUE_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
        }
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(UPTIME_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        // This task is only needed if running on an old SDK version.
        return !bortSystemCapabilities.supportsCaliperMetrics()
    }

    override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean = false

    companion object {
        private const val UPTIME_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.UPTIME_TICK"
        private const val UPTIME_WORK_TAG = "UPTIME_TICK"
        private val INTERVAL = 30.minutes
    }
}
