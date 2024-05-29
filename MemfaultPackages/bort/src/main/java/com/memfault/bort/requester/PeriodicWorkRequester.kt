package com.memfault.bort.requester

import androidx.work.WorkInfo
import androidx.work.WorkInfo.Companion.STOP_REASON_APP_STANDBY
import androidx.work.WorkInfo.Companion.STOP_REASON_BACKGROUND_RESTRICTION
import androidx.work.WorkInfo.Companion.STOP_REASON_CANCELLED_BY_APP
import androidx.work.WorkInfo.Companion.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW
import androidx.work.WorkInfo.Companion.STOP_REASON_CONSTRAINT_CHARGING
import androidx.work.WorkInfo.Companion.STOP_REASON_CONSTRAINT_CONNECTIVITY
import androidx.work.WorkInfo.Companion.STOP_REASON_CONSTRAINT_DEVICE_IDLE
import androidx.work.WorkInfo.Companion.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW
import androidx.work.WorkInfo.Companion.STOP_REASON_DEVICE_STATE
import androidx.work.WorkInfo.Companion.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED
import androidx.work.WorkInfo.Companion.STOP_REASON_NOT_STOPPED
import androidx.work.WorkInfo.Companion.STOP_REASON_PREEMPT
import androidx.work.WorkInfo.Companion.STOP_REASON_QUOTA
import androidx.work.WorkInfo.Companion.STOP_REASON_SYSTEM_PROCESSING
import androidx.work.WorkInfo.Companion.STOP_REASON_TIMEOUT
import androidx.work.WorkInfo.Companion.STOP_REASON_UNKNOWN
import androidx.work.WorkInfo.Companion.STOP_REASON_USER
import com.memfault.bort.DevMode
import com.memfault.bort.DumpsterCapabilities
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.FetchedSettingsUpdate
import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.settings.ReadonlyFetchedSettingsProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class PeriodicWorkRequester {
    protected abstract suspend fun startPeriodic(
        justBooted: Boolean,
        settingsChanged: Boolean,
    )

    protected abstract fun cancelPeriodic()

    /** Is the task enabled, based on the supplied settings? */
    protected abstract suspend fun enabled(settings: SettingsProvider): Boolean

    /** Did task scheduling parameters change? (Excluding anything covered by [enabled]) */
    protected abstract suspend fun parametersChanged(
        old: SettingsProvider,
        new: SettingsProvider,
    ): Boolean

    protected abstract suspend fun diagnostics(): BortWorkInfo

    /**
     * Manages scheduling/rescheding/cancelling all period tasks.
     *
     * This is here so that it (and only it) can call all the protected methods on
     * [PeriodicWorkRequester].
     */
    class PeriodicWorkManager @Inject constructor(
        private val periodicWorkRequesters: InjectSet<PeriodicWorkRequester>,
        private val dumpsterCapabilities: DumpsterCapabilities,
        private val cachedClientServerMode: CachedClientServerMode,
        private val devMode: DevMode,
        private val projectKeyProvider: ProjectKeyProvider,
        private val settingsProvider: SettingsProvider,
    ) {
        suspend fun maybeRestartTasksAfterSettingsChange(input: FetchedSettingsUpdate) {
            val old = DynamicSettingsProvider(
                object : ReadonlyFetchedSettingsProvider {
                    override fun get() = input.old
                },
                dumpsterCapabilities,
                cachedClientServerMode,
                devMode,
                projectKeyProvider,
            )
            val new = DynamicSettingsProvider(
                object : ReadonlyFetchedSettingsProvider {
                    override fun get() = input.new
                },
                dumpsterCapabilities,
                cachedClientServerMode,
                devMode,
                projectKeyProvider,
            )
            periodicWorkRequesters.forEach {
                val newEnabled = it.enabled(new)
                if (!newEnabled) {
                    it.cancelPeriodic()
                } else {
                    val oldEnabled = it.enabled(old)
                    if (!oldEnabled || it.parametersChanged(old, new)) {
                        it.startPeriodic(justBooted = false, settingsChanged = true)
                    }
                }
            }
            Logger.test("Periodic tasks were restarted")
        }

        suspend fun scheduleTasksAfterBootOrEnable(
            bortEnabled: Boolean,
            justBooted: Boolean,
        ) {
            periodicWorkRequesters.forEach {
                val enabled = it.enabled(settingsProvider) && bortEnabled
                if (enabled) {
                    it.startPeriodic(justBooted = justBooted, settingsChanged = false)
                } else {
                    it.cancelPeriodic()
                }
            }
        }

        suspend fun diagnostics(): List<BortWorkInfo> = periodicWorkRequesters.map { it.diagnostics() }
    }
}

data class BortWorkInfo(
    val name: String,
    val state: WorkInfo.State? = null,
    val runAttemptCount: Int? = null,
    val nextScheduleTimeMillis: Long? = null,
    val nextScheduleTimeLeft: Duration? = null,
    val stopReason: String? = null,
)

private fun Int.reasonString(): String = when (this) {
    // Copied from WorkInfo.kt
    STOP_REASON_NOT_STOPPED -> "STOP_REASON_NOT_STOPPED"
    STOP_REASON_UNKNOWN -> "STOP_REASON_UNKNOWN"
    STOP_REASON_CANCELLED_BY_APP -> "STOP_REASON_CANCELLED_BY_APP"
    STOP_REASON_PREEMPT -> "STOP_REASON_PREEMPT"
    STOP_REASON_TIMEOUT -> "STOP_REASON_TIMEOUT"
    STOP_REASON_DEVICE_STATE -> "STOP_REASON_DEVICE_STATE"
    STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW"
    STOP_REASON_CONSTRAINT_CHARGING -> "STOP_REASON_CONSTRAINT_CHARGING"
    STOP_REASON_CONSTRAINT_CONNECTIVITY -> "STOP_REASON_CONSTRAINT_CONNECTIVITY"
    STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "STOP_REASON_CONSTRAINT_DEVICE_IDLE"
    STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW"
    STOP_REASON_QUOTA -> "STOP_REASON_QUOTA"
    STOP_REASON_BACKGROUND_RESTRICTION -> "STOP_REASON_BACKGROUND_RESTRICTION"
    STOP_REASON_APP_STANDBY -> "STOP_REASON_APP_STANDBY"
    STOP_REASON_USER -> "STOP_REASON_USER"
    STOP_REASON_SYSTEM_PROCESSING -> "STOP_REASON_SYSTEM_PROCESSING"
    STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED"
    else -> "UNKNOWN_$this"
}

suspend fun Flow<List<WorkInfo>>.asBortWorkInfo(name: String): BortWorkInfo {
    return firstOrNull()
        ?.firstOrNull()
        ?.let {
            BortWorkInfo(
                name = name,
                state = it.state,
                runAttemptCount = it.runAttemptCount,
                nextScheduleTimeMillis = it.nextScheduleTimeMillis,
                nextScheduleTimeLeft = (it.nextScheduleTimeMillis - System.currentTimeMillis()).milliseconds,
                stopReason = it.stopReason.reasonString(),
            )
        }
        ?: BortWorkInfo(name).also { Logger.w("asBortWorkInfo: job not found for name '$name'") }
}
