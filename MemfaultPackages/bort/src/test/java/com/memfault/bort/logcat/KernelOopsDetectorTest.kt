package com.memfault.bort.logcat

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.HandleEventOfInterest
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration

class KernelOopsDetectorTest {
    private lateinit var detector: KernelOopsDetector
    private lateinit var mockHandleEventOfInterest: HandleEventOfInterest
    private lateinit var mockTokenBucketStore: TokenBucketStore
    private val packageManagerReport = PackageManagerReport()
    private val logcatSettings = object : LogcatSettings {
        override val dataSourceEnabled: Boolean
            get() = TODO("Not yet implemented")
        override val collectionInterval: Duration
            get() = TODO("Not yet implemented")
        override val commandTimeout: Duration
            get() = TODO("Not yet implemented")
        override val filterSpecs: List<LogcatFilterSpec>
            get() = TODO("Not yet implemented")
        override val kernelOopsDataSourceEnabled: Boolean
            get() = true
        override val kernelOopsRateLimitingSettings: RateLimitingSettings
            get() = TODO("Not yet implemented")
        override val storeUnsampled: Boolean
            get() = TODO("Not yet implemented")
        override val collectionMode: LogcatCollectionMode
            get() = TODO("Not yet implemented")
        override val continuousLogDumpThresholdBytes: Int
            get() = TODO("Not yet implemented")
        override val continuousLogDumpThresholdTime: Duration
            get() = TODO("Not yet implemented")
        override val continuousLogDumpWrappingTimeout: Duration
            get() = TODO("Not yet implemented")
        override val logs2metricsConfig: JsonObject
            get() = TODO("Not yet implemented")
    }

    @BeforeEach
    fun setUp() {
        mockHandleEventOfInterest = mockk(relaxed = true)
        mockTokenBucketStore = mockk()
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
            logcatSettings = logcatSettings,
        )
    }

    @Test
    fun detected() = runTest {
        detector.process(
            line = LogcatLine(message = "------------[ cut here ]------------", buffer = "kernel"),
            packageManagerReport = packageManagerReport,
        )
        assertTrue(detector.foundOops)
    }

    @Test
    fun notDetectedNonKernelBuffer() = runTest {
        detector.process(
            line = LogcatLine(message = "------------[ cut here ]------------", buffer = "main"),
            packageManagerReport = packageManagerReport,
        )
        assertFalse(detector.foundOops)
    }

    @Test
    fun notDetectedNonOopsKernelMessage() = runTest {
        detector.process(LogcatLine(message = "foo", buffer = "kernel"), packageManagerReport)
        assertFalse(detector.foundOops)
    }

    @Test
    fun finishNoOopsFound() = runTest {
        detector.finish(FakeCombinedTimeProvider.now())
        coVerify(exactly = 0) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseAbsoluteTime>()) }
    }

    @Test
    fun processIsNoopIfAlreadyFound() = runTest {
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
            logcatSettings = logcatSettings,
        )
        detector.foundOops = true
        val line: LogcatLine = mockk()
        detector.process(line, packageManagerReport)
        // line's properties are not accessed:
        verify(exactly = 0) { line.buffer }
        verify(exactly = 0) { line.message }
    }

    private fun mockLimitRate(limited: Boolean) {
        mockkStatic("com.memfault.bort.tokenbucket.TokenBucketStoreKt")
        every { mockTokenBucketStore.takeSimple(tag = any()) } returns !limited
    }

    @Test
    fun finishOopsFoundButRateLimited() = runTest {
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
            logcatSettings = logcatSettings,
        )
        detector.foundOops = true
        mockLimitRate(limited = true)

        detector.finish(FakeCombinedTimeProvider.now())
        coVerify(exactly = 0) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseAbsoluteTime>()) }
    }

    @Test
    fun finishOopsFound() = runTest {
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
            logcatSettings = logcatSettings,
        )
        detector.foundOops = true
        mockLimitRate(limited = false)

        val time = FakeCombinedTimeProvider.now()
        detector.finish(time)
        coVerify(exactly = 1) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseAbsoluteTime>()) }
    }
}
