package com.memfault.bort.dropbox

import com.memfault.bort.logcat.LogcatProcessor
import com.memfault.bort.logcat.LogcatProcessorResult
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatCollectionMode.CONTINUOUS
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ContinuousLogcatEntryProcessorTest {
    private lateinit var processor: ContinuousLogcatEntryProcessor
    private var logcatDataSourceEnabled: Boolean = true
    private val logcatProcessor: LogcatProcessor = mockk {
        coEvery { process(any(), any(), any()) } coAnswers {
            LogcatProcessorResult(timeStart = Instant.ofEpochMilli(1235), timeEnd = Instant.ofEpochMilli(1610973242000))
        }
    }

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        logcatDataSourceEnabled = true

        val logcatSettings = object : LogcatSettings {
            override val dataSourceEnabled: Boolean get() = logcatDataSourceEnabled
            override val collectionInterval: Duration = 1.seconds
            override val commandTimeout: Duration = 1.seconds
            override val filterSpecs: List<LogcatFilterSpec> = listOf()
            override val kernelOopsDataSourceEnabled: Boolean = true
            override val kernelOopsRateLimitingSettings: RateLimitingSettings =
                RateLimitingSettings(1, 1.seconds.boxed(), 1)
            override val storeUnsampled: Boolean = false
            override val collectionMode: LogcatCollectionMode = LogcatCollectionMode.CONTINUOUS
            override val continuousLogDumpThresholdBytes: Int = 128 * 1024
            override val continuousLogDumpThresholdTime: Duration = 30.minutes
            override val continuousLogDumpWrappingTimeout: Duration = 30.minutes
            override val logs2metricsConfig: JsonObject get() = TODO("not used")
        }

        processor = ContinuousLogcatEntryProcessor(
            logcatSettings = logcatSettings,
            tokenBucketStore = mockk {
                every { takeSimple(any(), any(), any()) } returns true
            },
            logcatProcessor = logcatProcessor,
            testDispatcher,
        )
    }

    @Test
    fun `happy path`() = runTest {
        val entry = mockEntry()
        processor.process(entry)
        coVerify(exactly = 1) { logcatProcessor.process(any(), any(), CONTINUOUS) }
    }

    @Test
    fun `disabled data source`() = runTest {
        logcatDataSourceEnabled = false
        confirmVerified(logcatProcessor)
    }
}
