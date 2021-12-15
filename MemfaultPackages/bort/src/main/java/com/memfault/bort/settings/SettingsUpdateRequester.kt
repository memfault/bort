package com.memfault.bort.settings

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
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
        val initialDelay = settingsDelay.toJavaDuration() + jitterDelayProvider.randomJitterDelay()
        setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }
}

@ContributesMultibinding(scope = SingletonComponent::class)
class SettingsUpdateRequester @Inject constructor(
    private val context: Application,
    private val settings: SettingsProvider,
    private val jitterDelayProvider: JitterDelayProvider,
    private val bortSystemCapabilities: BortSystemCapabilities,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        if (!bortSystemCapabilities.supportsDynamicSettings()) return

        restartPeriodicSettingsUpdate(
            context = context,
            httpApiSettings = settings.httpApiSettings,
            updateInterval = settings.settingsUpdateInterval,
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
