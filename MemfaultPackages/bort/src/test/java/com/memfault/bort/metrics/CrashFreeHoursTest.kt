package com.memfault.bort.metrics

import com.memfault.bort.settings.OperationalCrashesComponentGroup
import com.memfault.bort.settings.OperationalCrashesComponentGroups
import com.memfault.bort.settings.OperationalCrashesComponentGroupsProvider
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.time.boxed
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CrashFreeHoursTest {
    private var uptimeMs: Duration = 0.seconds
    private val timeProvider = object : BootRelativeTimeProvider {
        override fun now(): BootRelativeTime = BootRelativeTime(
            uptime = 0.hours.boxed(),
            elapsedRealtime = uptimeMs.boxed(),
            linuxBootId = "",
            bootCount = 0,
        )
    }
    private var storedState = CrashFreeHoursState()
    private val storage = object : CrashFreeHoursStorage {
        override var state: CrashFreeHoursState
            get() = storedState
            set(value) {
                storedState = value
            }
    }

    private var componentGroups = OperationalCrashesComponentGroups()
    private val operationalCrashesComponentGroupsProvider = OperationalCrashesComponentGroupsProvider {
        componentGroups
    }

    private val metricLogger: CrashFreeHoursMetricLogger = mockk(relaxed = true)
    private val crashFreeHours = CrashFreeHours(
        timeProvider = timeProvider,
        storage = storage,
        metricLogger = metricLogger,
        operationalCrashesComponentGroupsProvider = operationalCrashesComponentGroupsProvider,
    )

    private val crashTimestamp = Instant.now()

    @Test
    fun crashFreeHour() = runTest {
        crashFreeHours.onBoot()
        uptimeMs += 1.hours
        crashFreeHours.process()
        verify(exactly = 1) { metricLogger.incrementOperationalHours(1) }
        verify(exactly = 1) { metricLogger.incrementCrashFreeHours(1) }
        confirmVerified(metricLogger)
    }

    @Test
    fun crashyHour() = runTest {
        crashFreeHours.onBoot()
        uptimeMs += 30.minutes
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        uptimeMs += 30.minutes
        crashFreeHours.process()
        verify(exactly = 1) { metricLogger.incrementCrashes(null, timestamp = crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementOperationalHours(1) }
        verify(exactly = 0) { metricLogger.incrementCrashFreeHours(any()) }
        confirmVerified(metricLogger)
    }

    @Test
    fun lessThanOneHour() = runTest {
        crashFreeHours.onBoot()
        uptimeMs += 50.minutes
        crashFreeHours.process()
        verify(exactly = 0) { metricLogger.incrementOperationalHours(any()) }
        verify(exactly = 0) { metricLogger.incrementCrashFreeHours(any()) }
        confirmVerified(metricLogger)
    }

    @Test
    fun crashTriggersMetrics() = runTest {
        crashFreeHours.onBoot()
        uptimeMs += 60.minutes
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        verify(exactly = 1) { metricLogger.incrementCrashes(null, timestamp = crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementOperationalHours(1) }
        verify(exactly = 1) { metricLogger.incrementCrashFreeHours(1) }
        confirmVerified(metricLogger)
    }

    @Test
    fun crashyTriggersMetrics() = runTest {
        crashFreeHours.onBoot()
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        uptimeMs += 60.minutes
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        verify(exactly = 1) { metricLogger.incrementOperationalHours(1) }
        verify(exactly = 0) { metricLogger.incrementCrashFreeHours(any()) }
    }

    @Test
    fun multipleHours() = runTest {
        crashFreeHours.onBoot()
        uptimeMs += 3.hours
        crashFreeHours.process()
        verify { metricLogger.incrementOperationalHours(3) }
        uptimeMs += 30.minutes
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        uptimeMs += 30.minutes
        crashFreeHours.process()
        verify(exactly = 1) { metricLogger.incrementCrashes(null, timestamp = crashTimestamp.toEpochMilli()) }
        verify { metricLogger.incrementOperationalHours(1) }
        verify { metricLogger.incrementCrashFreeHours(3) }
        confirmVerified(metricLogger)
    }

    @Test
    fun multipleHoursMoreCrashes() = runTest {
        crashFreeHours.onBoot()
        uptimeMs += 2.hours + 50.minutes
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        verify { metricLogger.incrementOperationalHours(2) }
        verify { metricLogger.incrementCrashFreeHours(2) }
        uptimeMs += 10.minutes
        uptimeMs += 30.minutes
        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)
        uptimeMs += 30.minutes
        crashFreeHours.process()
        verify(exactly = 2) { metricLogger.incrementCrashes(null, timestamp = crashTimestamp.toEpochMilli()) }
        verify { metricLogger.incrementOperationalHours(1) }
        confirmVerified(metricLogger)
    }

    @Test
    fun crashComponentGroups_empty() = runTest {
        componentGroups = OperationalCrashesComponentGroups(groups = emptyList())

        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)

        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = null, crashTimestamp.toEpochMilli()) }
        confirmVerified(metricLogger)
    }

    @Test
    fun crashComponentGroups_nullMatchesFallback() = runTest {
        componentGroups = OperationalCrashesComponentGroups(
            groups = listOf(
                OperationalCrashesComponentGroup(name = "fallback", patterns = emptyList()),
            ),
        )

        crashFreeHours.onCrash(componentName = null, crashTimestamp = crashTimestamp)

        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = null, crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = "fallback", crashTimestamp.toEpochMilli()) }
    }

    @Test
    fun crashComponentGroups_emptyStringMatchesFallback() = runTest {
        componentGroups = OperationalCrashesComponentGroups(
            groups = listOf(
                OperationalCrashesComponentGroup(name = "fallback", patterns = emptyList()),
            ),
        )

        crashFreeHours.onCrash(componentName = "", crashTimestamp = crashTimestamp)

        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = null, crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = "fallback", crashTimestamp.toEpochMilli()) }
    }

    @Test
    fun crashComponentGroups_regexFullMatches() = runTest {
        componentGroups = OperationalCrashesComponentGroups(
            groups = listOf(
                OperationalCrashesComponentGroup(name = "apps", patterns = listOf("com.memfault")),
                OperationalCrashesComponentGroup(name = "fallback", patterns = emptyList()),
            ),
        )

        crashFreeHours.onCrash(componentName = "com.memfault.daemon", crashTimestamp = crashTimestamp)

        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = null, crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = "fallback", crashTimestamp.toEpochMilli()) }
    }

    @Test
    fun crashComponentGroups_regexMatches() = runTest {
        componentGroups = OperationalCrashesComponentGroups(
            groups = listOf(
                OperationalCrashesComponentGroup(name = "apps", patterns = listOf("com.memfault")),
                OperationalCrashesComponentGroup(name = "fallback", patterns = emptyList()),
            ),
        )

        crashFreeHours.onCrash(componentName = "com.memfault", crashTimestamp = crashTimestamp)

        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = null, crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = "apps", crashTimestamp.toEpochMilli()) }
    }

    @Test
    fun crashComponentGroups_regexes() = runTest {
        componentGroups = OperationalCrashesComponentGroups(
            groups = listOf(
                OperationalCrashesComponentGroup(name = "apps", patterns = listOf("com.memfault", "com.segfault")),
            ),
        )

        crashFreeHours.onCrash(componentName = "com.segfault", crashTimestamp = crashTimestamp)

        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = null, crashTimestamp.toEpochMilli()) }
        verify(exactly = 1) { metricLogger.incrementCrashes(componentName = "apps", crashTimestamp.toEpochMilli()) }
    }
}
