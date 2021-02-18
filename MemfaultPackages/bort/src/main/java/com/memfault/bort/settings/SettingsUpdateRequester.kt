package com.memfault.bort.settings

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.requester.PeriodicWorkRequester
import kotlin.time.Duration

private const val WORK_TAG = "SETTINGS_UPDATE"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.$WORK_TAG"

internal fun restartPeriodicSettingsUpdate(
    context: Context,
    httpApiSettings: HttpApiSettings,
    updateInterval: Duration,
) {
    periodicWorkRequest<SettingsUpdateTask>(
        updateInterval,
        workDataOf()
    ) {
        addTag(WORK_TAG)
        setConstraints(httpApiSettings.uploadConstraints)
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }
}

class SettingsUpdateRequester(
    private val context: Context,
    private val httpApiSettings: HttpApiSettings,
    private val getUpdateInterval: () -> Duration,
) : PeriodicWorkRequester() {
    override fun startPeriodic(justBooted: Boolean) {
        restartPeriodicSettingsUpdate(
            context = context,
            httpApiSettings = httpApiSettings,
            updateInterval = getUpdateInterval(),
        )
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override fun evaluateSettingsChange() {
    }
}
