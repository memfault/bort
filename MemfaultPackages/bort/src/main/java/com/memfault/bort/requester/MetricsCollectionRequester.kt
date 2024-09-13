package com.memfault.bort.requester

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.metrics.LastHeartbeatEndTimeProvider
import com.memfault.bort.metrics.MetricsCollectionTask
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.softwareVersionChanged
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

@ContributesMultibinding(SingletonComponent::class)
class MetricsCollectionRequester @Inject constructor(
    private val application: Application,
    private val lastHeartbeatEndTimeProvider: LastHeartbeatEndTimeProvider,
    private val metricsSettings: MetricsSettings,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val customMetrics: CustomMetrics,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(
        justBooted: Boolean,
        settingsChanged: Boolean,
    ) {
        val collectImmediately = if (justBooted) {
            val deviceSoftwareVersion = deviceInfoProvider.getDeviceInfo().softwareVersion

            // Collect metrics immediately if there was a heartbeat, and the heartbeat's software version
            // is not equal to the current device's software version. If there is no heartbeat then there's
            // no data to collect. If there is a heartbeat then if the software version is null or different from the
            // device's, finish that heartbeat and start a new one.
            customMetrics.softwareVersionChanged(deviceSoftwareVersion)
        } else {
            false
        }
        restartPeriodicCollection(
            resetLastHeartbeatTime = if (justBooted) bootRelativeTimeProvider.now() else null,
            collectImmediately = collectImmediately,
        )
    }

    fun restartPeriodicCollection(
        collectionInterval: Duration = maxOf(MINIMUM_COLLECTION_INTERVAL, metricsSettings.collectionInterval),
        resetLastHeartbeatTime: BootRelativeTime? = null,
        collectImmediately: Boolean,
    ) {
        Logger.test("Collecting metrics every ${collectionInterval.toDouble(DurationUnit.MINUTES)} minutes")

        resetLastHeartbeatTime?.let {
            lastHeartbeatEndTimeProvider.lastEnd = it
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
            WorkManager.getInstance(application)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    CANCEL_AND_REENQUEUE,
                    workRequest,
                )
        }
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        return settings.metricsSettings.dataSourceEnabled
    }

    override suspend fun diagnostics(): BortWorkInfo {
        return WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkFlow(WORK_UNIQUE_NAME_PERIODIC)
            .asBortWorkInfo("metrics")
    }

    override suspend fun parametersChanged(
        old: SettingsProvider,
        new: SettingsProvider,
    ): Boolean =
        old.metricsSettings.collectionInterval != new.metricsSettings.collectionInterval
}
