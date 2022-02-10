package com.memfault.bort.requester

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.metrics.RealLastHeartbeatEndTimeProvider
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
import kotlin.time.minutes

private const val WORK_TAG = "METRICS_COLLECTION"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.METRICS_COLLECTION"
private val MINIMUM_COLLECTION_INTERVAL = 15.minutes

internal fun restartPeriodicMetricsCollection(
    context: Context,
    collectionInterval: Duration,
    lastHeartbeatEnd: BootRelativeTime,
    collectImmediately: Boolean = false, // for testing
) {
    RealLastHeartbeatEndTimeProvider(
        PreferenceManager.getDefaultSharedPreferences(context)
    ).lastEnd = lastHeartbeatEnd

    periodicWorkRequest<MetricsCollectionTask>(
        collectionInterval,
        workDataOf()
    ) {
        addTag(WORK_TAG)
        if (!collectImmediately) {
            setInitialDelay(collectionInterval.inMilliseconds.toLong(), TimeUnit.MILLISECONDS)
        }
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }
}

@ContributesMultibinding(SingletonComponent::class)
class MetricsCollectionRequester @Inject constructor(
    private val context: Context,
    private val metricsSettings: MetricsSettings,
    private val bortSystemCapabilities: BortSystemCapabilities,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        if (!metricsSettings.dataSourceEnabled) return
        if (!bortSystemCapabilities.supportsCaliperMetrics()) return

        val collectionInterval = maxOf(MINIMUM_COLLECTION_INTERVAL, metricsSettings.collectionInterval)
        Logger.test("Collecting metrics every ${collectionInterval.inMinutes} minutes")

        restartPeriodicMetricsCollection(
            context = context,
            collectionInterval = collectionInterval,
            lastHeartbeatEnd = bootRelativeTimeProvider.now(),
        )
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.metricsSettings.dataSourceEnabled != new.metricsSettings.dataSourceEnabled ||
            old.metricsSettings.collectionInterval != new.metricsSettings.collectionInterval
}
