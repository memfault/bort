package com.memfault.bort.logcat

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

data class FakeNextLogcatStartTimeProvider(
    override var nextStart: BaseAbsoluteTime,
) : NextLogcatStartTimeProvider

class PeriodicLogcatCollectorTest {
    private lateinit var collector: PeriodicLogcatCollector
    private lateinit var startTimeProvider: NextLogcatStartTimeProvider
    private lateinit var logcatSettings: LogcatSettings
    private lateinit var periodicLogcatRunner: PeriodicLogcatRunner
    private val logcatProcessor: LogcatProcessor = mockk {
        coEvery { process(any(), any(), any()) } coAnswers {
            LogcatProcessorResult(timeStart = Instant.ofEpochMilli(1235), timeEnd = Instant.ofEpochMilli(1610973242000))
        }
    }
    private val inputStream: InputStream = mockk()

    @Before
    fun setUp() {
        periodicLogcatRunner = mockk(relaxed = true) {
            coEvery {
                runLogcat(any(), any(), any(), any<suspend (InputStream) -> Unit>())
            } coAnswers {
                val handler = arg<suspend (InputStream) -> Unit>(3)
                handler(inputStream)
            }
        }
        startTimeProvider = FakeNextLogcatStartTimeProvider(nextStart = AbsoluteTime(Instant.ofEpochSecond(0)))

        logcatSettings = object : LogcatSettings {
            override val dataSourceEnabled = true
            override val collectionInterval = ZERO
            override val commandTimeout = ZERO
            override val filterSpecs = emptyList<LogcatFilterSpec>()
            override val kernelOopsDataSourceEnabled = true
            override val kernelOopsRateLimitingSettings = RateLimitingSettings(0, ZERO.boxed(), 0)
            override val storeUnsampled: Boolean = false
            override val collectionMode = PERIODIC
            override val continuousLogDumpThresholdBytes: Int = 128 * 1024
            override val continuousLogDumpThresholdTime: Duration = 30.minutes
            override val continuousLogDumpWrappingTimeout: Duration = 30.minutes
            override val logs2metricsConfig: JsonObject get() = TODO("not used")
        }

        collector = PeriodicLogcatCollector(
            nextLogcatStartTimeProvider = startTimeProvider,
            logcatSettings = logcatSettings,
            periodicLogcatRunner = periodicLogcatRunner,
            logcatProcessor = logcatProcessor,
            ioDispatcher = Dispatchers.IO,
        )
    }

    @Test
    fun happyPath() = runTest {
        val nextStartInstant = Instant.ofEpochSecond(1234, 56789)
        startTimeProvider.nextStart = AbsoluteTime(nextStartInstant)
        collector.collect()
        assertThat(
            startTimeProvider.nextStart,
        ).isEqualTo(
            AbsoluteTime(Instant.ofEpochSecond(1610973242)),
        )
        coVerify(exactly = 1) { logcatProcessor.process(inputStream, any(), PERIODIC) }
    }
}
