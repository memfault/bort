package com.memfault.bort.settings

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.JitterDelayProvider
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.shared.Logger
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

private const val WORK_TAG = "SETTINGS_UPDATE"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.$WORK_TAG"

internal fun restartPeriodicSettingsUpdate(
    context: Context,
    httpApiSettings: HttpApiSettings,
    updateInterval: Duration,
    delayAfterSettingsUpdate: Boolean,
    testRequest: Boolean = false,
    jitterDelayProvider: JitterDelayProvider,
) {
    if (testRequest) {
        Logger.test("Restarting settings periodic task for testing")
    } else {
        Logger.test(
            "Requesting settings every ${updateInterval.inHours} hours (delayInitially=$delayAfterSettingsUpdate)"
        )
    }

    periodicWorkRequest<SettingsUpdateTask>(
        updateInterval,
        workDataOf()
    ) {
        addTag(WORK_TAG)
        setConstraints(httpApiSettings.uploadConstraints)
        // Use delay to prevent running the task again immediately after a settings update:
        val settingsDelay = if (delayAfterSettingsUpdate) updateInterval else ZERO
        val initialDelay = settingsDelay + jitterDelayProvider.randomJitterDelay()
        setInitialDelay(initialDelay.toLongMilliseconds(), TimeUnit.MILLISECONDS)
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
    private val jitterDelayProvider: JitterDelayProvider,
    private val bortSystemCapabilities: BortSystemCapabilities,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        if (!bortSystemCapabilities.supportsDynamicSettings()) return

        restartPeriodicSettingsUpdate(
            context = context,
            httpApiSettings = httpApiSettings,
            updateInterval = getUpdateInterval(),
            delayAfterSettingsUpdate = settingsChanged,
            jitterDelayProvider = jitterDelayProvider,
        )
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.settingsUpdateInterval != new.settingsUpdateInterval
}
