package com.memfault.bort.requester

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.logcat.LogcatCollectionTask
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.logcat.NextLogcatStartTimeProvider
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.toAbsoluteTime
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

private const val WORK_TAG = "LOGCAT_COLLECTION"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.LOGCAT_COLLECTION"
private val MINIMUM_COLLECTION_INTERVAL = 15.minutes

internal fun restartPeriodicLogcatCollection(
    context: Context,
    nextLogcatCidProvider: NextLogcatCidProvider,
    nextLogcatStartTimeProvider: NextLogcatStartTimeProvider,
    collectionInterval: Duration,
    lastLogcatEnd: AbsoluteTime,
    collectImmediately: Boolean = false, // for testing
) {
    nextLogcatCidProvider.rotate()
    nextLogcatStartTimeProvider.nextStart = lastLogcatEnd

    periodicWorkRequest<LogcatCollectionTask>(
        collectionInterval,
        workDataOf(),
    ) {
        addTag(WORK_TAG)
        if (!collectImmediately) {
            setInitialDelay(collectionInterval.toDouble(DurationUnit.MILLISECONDS).toLong(), TimeUnit.MILLISECONDS)
        }
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
    }
}

@ContributesMultibinding(SingletonComponent::class)
class LogcatCollectionRequester @Inject constructor(
    private val application: Application,
    private val logcatSettings: LogcatSettings,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val nextLogcatStartTimeProvider: NextLogcatStartTimeProvider,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(
        justBooted: Boolean,
        settingsChanged: Boolean,
    ) {
        val collectionInterval = maxOf(MINIMUM_COLLECTION_INTERVAL, logcatSettings.collectionInterval)
        Logger.test("Collecting logcat every ${collectionInterval.toDouble(DurationUnit.MINUTES)} minutes")

        // MFLT-2753: after booting, attempt to collect logs from pstore (logcat -L)

        restartPeriodicLogcatCollection(
            context = application,
            nextLogcatCidProvider = nextLogcatCidProvider,
            nextLogcatStartTimeProvider = nextLogcatStartTimeProvider,
            collectionInterval = collectionInterval,
            lastLogcatEnd = if (justBooted) 0L.toAbsoluteTime() else AbsoluteTime.now(),
        )
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean {
        return settings.logcatSettings.dataSourceEnabled &&
            settings.logcatSettings.collectionMode == LogcatCollectionMode.PERIODIC
    }

    override suspend fun diagnostics(): BortWorkInfo {
        return WorkManager.getInstance(application)
            .getWorkInfosForUniqueWorkFlow(WORK_UNIQUE_NAME_PERIODIC)
            .asBortWorkInfo("logcat")
    }

    override suspend fun parametersChanged(
        old: SettingsProvider,
        new: SettingsProvider,
    ): Boolean =
        old.logcatSettings.collectionInterval != new.logcatSettings.collectionInterval

    suspend fun isScheduledAt(settings: SettingsProvider): Duration? =
        if (enabled(settings)) logcatSettings.collectionInterval else null
}
