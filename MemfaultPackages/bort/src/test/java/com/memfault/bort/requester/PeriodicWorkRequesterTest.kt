package com.memfault.bort.requester

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.memfault.bort.DevMode
import com.memfault.bort.DumpsterCapabilities
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.FetchedSettingsUpdate
import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.settings.ReadonlyFetchedSettingsProvider
import com.memfault.bort.settings.SETTINGS_FIXTURE
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.toSettings
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class PeriodicWorkRequesterTest {
    private val requester = object : PeriodicWorkRequester() {
        var started = false
        var cancelled = false
        var enabled = false
        var enabledOld = false
        var paramsChanged = false

        override suspend fun startPeriodic(
            justBooted: Boolean,
            settingsChanged: Boolean,
        ) {
            started = true
        }

        override fun cancelPeriodic() {
            cancelled = true
        }

        override suspend fun enabled(settings: SettingsProvider): Boolean =
            if (settings.metricsSettings.maxNumAppVersions == 1) {
                enabledOld
            } else {
                enabled
            }

        override suspend fun parametersChanged(
            old: SettingsProvider,
            new: SettingsProvider,
        ): Boolean = paramsChanged

        override suspend fun diagnostics(): BortWorkInfo {
            TODO("Not used")
        }
    }
    val oldSettings = SETTINGS_FIXTURE.toSettings().copy(metricsMaxNumAppVersions = 1)
    val newSettings = SETTINGS_FIXTURE.toSettings().copy(metricsMaxNumAppVersions = 2)
    val oldAndNewSettings = FetchedSettingsUpdate(
        old = oldSettings,
        new = newSettings,
    )
    private val requesters = setOf(requester)
    private val dumpsterCapabilities: DumpsterCapabilities = mockk()
    private val devMode: DevMode = mockk()
    private val projectKeyProvider: ProjectKeyProvider = mockk()
    private val settingsProvider = DynamicSettingsProvider(
        object : ReadonlyFetchedSettingsProvider {
            override fun get() = newSettings
        },
        dumpsterCapabilities,
        devMode,
        projectKeyProvider,
    )
    private val manager = PeriodicWorkManager(
        periodicWorkRequesters = requesters,
        dumpsterCapabilities = dumpsterCapabilities,
        devMode = devMode,
        projectKeyProvider = projectKeyProvider,
        settingsProvider = settingsProvider,
    )

    @Test
    fun cancelsTaskAfterSettingsChange() = runTest {
        requester.enabled = false
        requester.enabledOld = false
        requester.paramsChanged = false
        manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
        assertThat(requester.cancelled).isTrue()
        assertThat(requester.started).isFalse()
    }

    @Test
    fun reschedulesTaskAfterSettingsChange_wasStopped() = runTest {
        requester.enabled = true
        requester.enabledOld = false
        requester.paramsChanged = false
        manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
        assertThat(requester.cancelled).isFalse()
        assertThat(requester.started).isTrue()
    }

    @Test
    fun reschedulesTaskAfterSettingsChange_paramsChanged() = runTest {
        requester.enabled = true
        requester.enabledOld = true
        requester.paramsChanged = true
        manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
        assertThat(requester.cancelled).isFalse()
        assertThat(requester.started).isTrue()
    }

    @Test
    fun doesNothingAfterSettingsChange() = runTest {
        requester.enabled = true
        requester.enabledOld = true
        requester.paramsChanged = false
        manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
        assertThat(requester.cancelled).isFalse()
        assertThat(requester.started).isFalse()
    }

    @Test
    fun schedulesAfterBoot() = runTest {
        requester.enabled = true
        manager.scheduleTasksAfterBootOrEnable(bortEnabled = true, justBooted = true)
        assertThat(requester.cancelled).isFalse()
        assertThat(requester.started).isTrue()
    }

    @Test
    fun cancelsAfterBoot() = runTest {
        requester.enabled = false
        manager.scheduleTasksAfterBootOrEnable(bortEnabled = true, justBooted = true)
        assertThat(requester.cancelled).isTrue()
        assertThat(requester.started).isFalse()
    }

    @Test
    fun cancelsAfterBortDisable() = runTest {
        requester.enabled = true
        manager.scheduleTasksAfterBootOrEnable(bortEnabled = false, justBooted = false)
        assertThat(requester.cancelled).isTrue()
        assertThat(requester.started).isFalse()
    }
}
