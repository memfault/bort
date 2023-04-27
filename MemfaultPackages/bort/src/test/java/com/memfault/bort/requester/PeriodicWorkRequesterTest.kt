package com.memfault.bort.requester

import com.memfault.bort.DevMode
import com.memfault.bort.DumpsterCapabilities
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.FetchedSettingsUpdate
import com.memfault.bort.settings.ProjectKeyProvider
import com.memfault.bort.settings.ReadonlyFetchedSettingsProvider
import com.memfault.bort.settings.SETTINGS_FIXTURE
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.toSettings
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class PeriodicWorkRequesterTest {
    private val requester = object : PeriodicWorkRequester() {
        var started = false
        var cancelled = false
        var enabled = false
        var enabledOld = false
        var paramsChanged = false

        override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
            started = true
        }

        override fun cancelPeriodic() {
            cancelled = true
        }

        override suspend fun enabled(settings: SettingsProvider): Boolean {
            return if (settings.metricsSettings.maxNumAppVersions == 1) enabledOld else enabled
        }

        override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean {
            return paramsChanged
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
    private val cachedClientServerMode: CachedClientServerMode = mockk()
    private val devMode: DevMode = mockk()
    private val projectKeyProvider: ProjectKeyProvider = mockk()
    private val settingsProvider = DynamicSettingsProvider(
        object : ReadonlyFetchedSettingsProvider {
            override fun get() = newSettings
        },
        dumpsterCapabilities,
        cachedClientServerMode,
        devMode,
        projectKeyProvider,
    )
    private val manager = PeriodicWorkManager(
        periodicWorkRequesters = requesters,
        dumpsterCapabilities = dumpsterCapabilities,
        cachedClientServerMode = cachedClientServerMode,
        devMode = devMode,
        projectKeyProvider = projectKeyProvider,
        settingsProvider = settingsProvider,
    )

    @Test
    fun cancelsTaskAfterSettingsChange() {
        runTest {
            requester.enabled = false
            requester.enabledOld = false
            requester.paramsChanged = false
            manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
            assertTrue(requester.cancelled)
            assertFalse(requester.started)
        }
    }

    @Test
    fun reschedulesTaskAfterSettingsChange_wasStopped() {
        runTest {
            requester.enabled = true
            requester.enabledOld = false
            requester.paramsChanged = false
            manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
            assertFalse(requester.cancelled)
            assertTrue(requester.started)
        }
    }

    @Test
    fun reschedulesTaskAfterSettingsChange_paramsChanged() {
        runTest {
            requester.enabled = true
            requester.enabledOld = true
            requester.paramsChanged = true
            manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
            assertFalse(requester.cancelled)
            assertTrue(requester.started)
        }
    }

    @Test
    fun doesNothingAfterSettingsChange() {
        runTest {
            requester.enabled = true
            requester.enabledOld = true
            requester.paramsChanged = false
            manager.maybeRestartTasksAfterSettingsChange(oldAndNewSettings)
            assertFalse(requester.cancelled)
            assertFalse(requester.started)
        }
    }

    @Test
    fun schedulesAfterBoot() {
        runTest {
            requester.enabled = true
            manager.scheduleTasksAfterBootOrEnable(bortEnabled = true, justBooted = true)
            assertFalse(requester.cancelled)
            assertTrue(requester.started)
        }
    }

    @Test
    fun cancelsAfterBoot() {
        runTest {
            requester.enabled = false
            manager.scheduleTasksAfterBootOrEnable(bortEnabled = true, justBooted = true)
            assertTrue(requester.cancelled)
            assertFalse(requester.started)
        }
    }

    @Test
    fun cancelsAfterBortDisable() {
        runTest {
            requester.enabled = true
            manager.scheduleTasksAfterBootOrEnable(bortEnabled = false, justBooted = false)
            assertTrue(requester.cancelled)
            assertFalse(requester.started)
        }
    }
}
