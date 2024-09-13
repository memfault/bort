package com.memfault.bort.metrics

import com.memfault.bort.uploader.BortEnabledTestProvider
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MetricsPollerTest {
    private val bortEnabledProvider = BortEnabledTestProvider()
    private val pollingIntervalFlow = MutableStateFlow(1.seconds)
    private val collector: MetricCollector = mockk(relaxed = true)
    val metricsPoller = MetricsPoller(
        bortEnabledProvider = bortEnabledProvider,
        metricsPollingInterval = { pollingIntervalFlow },
        collectors = setOf(collector),
        ioDispatcher = Dispatchers.IO, // not actually used in this test
    )

    @Test
    fun doesNotPollWhenDisabled() = runTest {
        bortEnabledProvider.setEnabled(false)
        metricsPoller.runPoller(backgroundScope)
        advanceTimeBy(2.seconds)
        coVerify(exactly = 0) { collector.collect() }
    }

    @Test
    fun pollsWhenEnabled() = runTest {
        metricsPoller.runPoller(backgroundScope)
        advanceTimeBy(0.5.seconds)
        coVerify(atLeast = 1) { collector.collect() }
    }

    @Test
    fun pollsSeveralTimesWhenEnabled() = runTest {
        metricsPoller.runPoller(backgroundScope)
        advanceTimeBy(10.seconds)
        coVerify(atLeast = 9) { collector.collect() }
    }

    @Test
    fun disabledThenEnabled() = runTest {
        bortEnabledProvider.setEnabled(false)
        metricsPoller.runPoller(backgroundScope)
        advanceTimeBy(2.seconds)
        coVerify(exactly = 0) { collector.collect() }

        bortEnabledProvider.setEnabled(true)
        advanceTimeBy(2.seconds)
        coVerify(atLeast = 1) { collector.collect() }
    }

    @Test
    fun enabledThenDisabledThenEnabled() = runTest {
        metricsPoller.runPoller(backgroundScope)
        advanceTimeBy(0.5.seconds)
        coVerify(exactly = 1) { collector.collect() }

        bortEnabledProvider.setEnabled(false)
        advanceTimeBy(10.seconds)
        // No more calls
        coVerify(exactly = 1) { collector.collect() }

        bortEnabledProvider.setEnabled(true)
        advanceTimeBy(1.seconds)
        coVerify(atLeast = 2) { collector.collect() }
    }

    @Test
    fun longerInterval() = runTest {
        pollingIntervalFlow.value = 30.seconds
        metricsPoller.runPoller(backgroundScope)
        advanceTimeBy(1.seconds)
        coVerify(exactly = 1) { collector.collect() }
        advanceTimeBy(28.seconds)
        coVerify(exactly = 1) { collector.collect() }
        advanceTimeBy(2.seconds)
        coVerify(exactly = 2) { collector.collect() }
    }
}
