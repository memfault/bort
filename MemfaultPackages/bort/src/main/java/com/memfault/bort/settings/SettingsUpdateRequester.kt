package com.memfault.bort.settings

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.isClient
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.requester.BortWorkInfo
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.requester.asBortWorkInfo
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit.HOURS

private const val WORK_TAG = "SETTINGS_UPDATE"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.$WORK_TAG"

@ContributesMultibinding(scope = SingletonComponent::class)
class SettingsUpdateRequester @Inject constructor(
    private val application: Application,
    private val httpApiSettings: HttpApiSettings,
    private val jitterDelayProvider: JitterDelayProvider,
    private val cachedClientServerMode: CachedClientServerMode,
    private val everFetchedSettingsPreferenceProvider: EverFetchedSettingsPreferenceProvider,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(
        justBooted: Boolean,
        settingsChanged: Boolean,
    ) {
        restartSettingsUpdate(delayAfterSettingsUpdate = settingsChanged, cancel = false)
    }

    fun restartSettingsUpdate(
        delayAfterSettingsUpdate: Boolean,
        cancel: Boolean,
    ) {
        restartPeriodicSettingsUpdate(
            updateInterval = httpApiSettings.deviceConfigInterval,
            delayAfterSettingsUpdate = delayAfterSettingsUpdate,
            cancel = cancel,
        )
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        // Client does not fetch settings - server will forward them
        return !cachedClientServerMode.isClient()
    }

    override suspend fun diagnostics(): BortWorkInfo = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(WORK_UNIQUE_NAME_PERIODIC)
        .asBortWorkInfo("settings")

    override suspend fun parametersChanged(
        old: SettingsProvider,
        new: SettingsProvider,
    ): Boolean =
        old.httpApiSettings.deviceConfigInterval != new.httpApiSettings.deviceConfigInterval

    fun restartPeriodicSettingsUpdate(
        updateInterval: Duration,
        delayAfterSettingsUpdate: Boolean,
        cancel: Boolean,
        testRequest: Boolean = false,
    ) {
        if (testRequest) {
            Logger.test("Restarting settings periodic task for testing")
        } else {
            Logger.test(
                "Requesting settings every ${updateInterval.toDouble(HOURS)} " +
                    "hours (delayInitially=$delayAfterSettingsUpdate)",
            )
        }

        periodicWorkRequest<SettingsUpdateTask>(
            updateInterval,
            workDataOf(),
        ) {
            addTag(WORK_TAG)
            setConstraints(httpApiSettings.uploadConstraints)
            // Only add jitter if we have previously fetched settings (i.e. zero delay if we have never fetched
            // settings)
            if (everFetchedSettingsPreferenceProvider.getValue()) {
                // Use delay to prevent running the task again immediately after a settings update:
                val settingsDelay = if (delayAfterSettingsUpdate) updateInterval else ZERO
                val initialDelay = settingsDelay +
                    jitterDelayProvider.randomJitterDelay(maxDelay = updateInterval)
                setInitialDelay(initialDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }
        }.also { workRequest ->
            WorkManager.getInstance(application)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    if (cancel) CANCEL_AND_REENQUEUE else UPDATE,
                    workRequest,
                )
        }
    }
}
