package com.memfault.bort.requester

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.logcat.LogcatCollectionTask
import com.memfault.bort.logcat.RealNextLogcatCidProvider
import com.memfault.bort.logcat.RealNextLogcatStartTimeProvider
import com.memfault.bort.periodicWorkRequest
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
    collectionInterval: Duration,
    lastLogcatEnd: AbsoluteTime,
    collectImmediately: Boolean = false, // for testing
) {
    PreferenceManager.getDefaultSharedPreferences(context).let {
        RealNextLogcatCidProvider(it).rotate()
        RealNextLogcatStartTimeProvider(it).nextStart = lastLogcatEnd
    }

    periodicWorkRequest<LogcatCollectionTask>(
        collectionInterval,
        workDataOf()
    ) {
        addTag(WORK_TAG)
        if (!collectImmediately) {
            setInitialDelay(collectionInterval.toDouble(DurationUnit.MILLISECONDS).toLong(), TimeUnit.MILLISECONDS)
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
class LogcatCollectionRequester @Inject constructor(
    private val context: Context,
    private val logcatSettings: LogcatSettings,
    private val bortSystemCapabilities: BortSystemCapabilities,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        if (!logcatSettings.dataSourceEnabled) return
        if (!bortSystemCapabilities.supportsCaliperLogcatCollection()) return

        val collectionInterval = maxOf(MINIMUM_COLLECTION_INTERVAL, logcatSettings.collectionInterval)
        Logger.test("Collecting logcat every ${collectionInterval.toDouble(DurationUnit.MINUTES)} minutes")

        // MFLT-2753: after booting, attempt to collect logs from pstore (logcat -L)

        restartPeriodicLogcatCollection(
            context = context,
            collectionInterval = collectionInterval,
            lastLogcatEnd = if (justBooted) 0L.toAbsoluteTime() else AbsoluteTime.now(),
        )
    }

    override fun cancelPeriodic() {
        Logger.test("Cancelling $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override fun restartRequired(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.logcatSettings.dataSourceEnabled != new.logcatSettings.dataSourceEnabled ||
            old.logcatSettings.collectionInterval != new.logcatSettings.collectionInterval
}
