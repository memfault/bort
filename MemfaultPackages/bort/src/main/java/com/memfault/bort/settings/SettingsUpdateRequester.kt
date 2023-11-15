package com.memfault.bort.settings

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.isClient
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit.HOURS
import kotlin.time.toJavaDuration

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
        // Use delay to prevent running the task again immediately after a settings update:
        val settingsDelay = if (delayAfterSettingsUpdate) updateInterval else ZERO
        val initialDelay = settingsDelay.toJavaDuration() + jitterDelayProvider.randomJitterDelay()
        setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
    }
}

@ContributesMultibinding(scope = SingletonComponent::class)
class SettingsUpdateRequester @Inject constructor(
    private val application: Application,
    private val settings: SettingsProvider,
    private val jitterDelayProvider: JitterDelayProvider,
    private val bortSystemCapabilities: BortSystemCapabilities,
    private val cachedClientServerMode: CachedClientServerMode,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        restartSettingsUpdate(delayAfterSettingsUpdate = settingsChanged)
    }

    suspend fun restartSettingsUpdate(delayAfterSettingsUpdate: Boolean) {
        restartPeriodicSettingsUpdate(
            context = application,
            httpApiSettings = settings.httpApiSettings,
            updateInterval = settings.sdkSettingsUpdateInterval(),
            delayAfterSettingsUpdate = delayAfterSettingsUpdate,
            jitterDelayProvider = jitterDelayProvider,
        )
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        return bortSystemCapabilities.supportsDynamicSettings() &&
            // Client does not fetch settings - server will forward them.
            !cachedClientServerMode.isClient()
    }

    override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.sdkSettingsUpdateInterval() != new.sdkSettingsUpdateInterval()
}

/**
 * Get the correct update interval, based on which endpoint is enabled.
 */
private suspend fun SettingsProvider.sdkSettingsUpdateInterval(): Duration = when (httpApiSettings.useDeviceConfig()) {
    true -> httpApiSettings.deviceConfigInterval
    false -> settingsUpdateInterval
}
