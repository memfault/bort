package com.memfault.bort.requester

import com.memfault.bort.DevMode
import com.memfault.bort.DumpsterCapabilities
import com.memfault.bort.InjectSet
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.FetchedSettingsUpdate
import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.settings.ReadonlyFetchedSettingsProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import javax.inject.Inject

abstract class PeriodicWorkRequester {
    protected abstract suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean)
    protected abstract fun cancelPeriodic()

    /** Is the task enabled, based on the supplied settings? */
    protected abstract suspend fun enabled(settings: SettingsProvider): Boolean
    /** Did task scheduling parameters change? (Excluding anything covered by [enabled]) */
    protected abstract suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean

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

        suspend fun scheduleTasksAfterBootOrEnable(bortEnabled: Boolean, justBooted: Boolean) {
            periodicWorkRequesters.forEach {
                val enabled = it.enabled(settingsProvider) && bortEnabled
                if (enabled) {
                    it.startPeriodic(justBooted = justBooted, settingsChanged = false)
                } else {
                    it.cancelPeriodic()
                }
            }
        }
    }
}
