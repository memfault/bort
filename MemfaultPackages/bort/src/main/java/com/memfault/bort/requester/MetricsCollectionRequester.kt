package com.memfault.bort.requester

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.MetricsSettings
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.metrics.RealLastHeartbeatEndTimeProvider
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.RealBootRelativeTimeProvider
import java.util.concurrent.TimeUnit
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

class MetricsCollectionRequester(
    private val context: Context,
    private val metricsSettings: MetricsSettings,
) : PeriodicWorkRequester() {
    override fun startPeriodic(justBooted: Boolean) {
        if (!metricsSettings.dataSourceEnabled) return

        val collectionInterval = maxOf(MINIMUM_COLLECTION_INTERVAL, metricsSettings.collectionInterval)
        Logger.test("Collecting metrics every ${collectionInterval.inMinutes} minutes")

        restartPeriodicMetricsCollection(
            context = context,
            collectionInterval = collectionInterval,
            lastHeartbeatEnd = RealBootRelativeTimeProvider(context).now(),
        )
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }
}
