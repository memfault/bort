package com.memfault.bort.requester

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.metrics.LastHeartbeatEndTimeProvider
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

private const val WORK_TAG = "METRICS_COLLECTION"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.METRICS_COLLECTION"
private val MINIMUM_COLLECTION_INTERVAL = 15.minutes

internal fun restartPeriodicMetricsCollection(
    context: Context,
    lastHeartbeatEndTimeProvider: LastHeartbeatEndTimeProvider,
    collectionInterval: Duration,
    lastHeartbeatEnd: BootRelativeTime? = null,
    collectImmediately: Boolean = false,
    cancel: Boolean,
) {
    lastHeartbeatEnd?.let {
        lastHeartbeatEndTimeProvider.lastEnd = lastHeartbeatEnd
    }

    periodicWorkRequest<MetricsCollectionTask>(
        collectionInterval,
        workDataOf(),
    ) {
        addTag(WORK_TAG)
        if (!collectImmediately) {
            setInitialDelay(collectionInterval.toLong(DurationUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
        }
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                if (cancel) CANCEL_AND_REENQUEUE else UPDATE,
                workRequest,
            )
    }
}

@ContributesMultibinding(SingletonComponent::class)
class MetricsCollectionRequester @Inject constructor(
    private val application: Application,
    private val lastHeartbeatEndTimeProvider: LastHeartbeatEndTimeProvider,
    private val metricsSettings: MetricsSettings,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        restartPeriodicCollection(resetLastHeartbeatTime = justBooted, collectImmediately = false)
    }

    fun restartPeriodicCollection(resetLastHeartbeatTime: Boolean, collectImmediately: Boolean) {
        val collectionInterval = maxOf(MINIMUM_COLLECTION_INTERVAL, metricsSettings.collectionInterval)
        Logger.test("Collecting metrics every ${collectionInterval.toDouble(DurationUnit.MINUTES)} minutes")

        restartPeriodicMetricsCollection(
            context = application,
            lastHeartbeatEndTimeProvider = lastHeartbeatEndTimeProvider,
            collectionInterval = collectionInterval,
            lastHeartbeatEnd = if (resetLastHeartbeatTime) bootRelativeTimeProvider.now() else null,
            collectImmediately = collectImmediately,
            cancel = true,
        )
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        return settings.metricsSettings.dataSourceEnabled
    }

    override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.metricsSettings.collectionInterval != new.metricsSettings.collectionInterval
}
