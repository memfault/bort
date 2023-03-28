package com.memfault.bort.logcat

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.HandleEventOfInterest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KernelOopsDetectorTest {
    lateinit var detector: KernelOopsDetector
    lateinit var mockHandleEventOfInterest: HandleEventOfInterest
    lateinit var mockTokenBucketStore: TokenBucketStore

    @BeforeEach
    fun setUp() {
        mockHandleEventOfInterest = mockk(relaxed = true)
        mockTokenBucketStore = mockk()
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
        )
    }

    @Test
    fun detected() {
        detector.process(LogcatLine(message = "------------[ cut here ]------------", buffer = "kernel"))
        assertTrue(detector.foundOops)
    }

    @Test
    fun notDetectedNonKernelBuffer() {
        detector.process(LogcatLine(message = "------------[ cut here ]------------", buffer = "main"))
        assertFalse(detector.foundOops)
    }

    @Test
    fun notDetectedNonOopsKernelMessage() {
        detector.process(LogcatLine(message = "foo", buffer = "kernel"))
        assertFalse(detector.foundOops)
    }

    @Test
    fun finishNoOopsFound() {
        detector.finish(FakeCombinedTimeProvider.now())
        verify(exactly = 0) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseAbsoluteTime>()) }
    }

    @Test
    fun processIsNoopIfAlreadyFound() {
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
        )
        detector.foundOops = true
        val line: LogcatLine = mockk()
        detector.process(line)
        // line's properties are not accessed:
        verify(exactly = 0) { line.buffer }
        verify(exactly = 0) { line.message }
    }

    private fun mockLimitRate(limited: Boolean) {
        mockkStatic("com.memfault.bort.tokenbucket.TokenBucketStoreKt")
        every { mockTokenBucketStore.takeSimple(tag = any()) } returns !limited
    }

    @Test
    fun finishOopsFoundButRateLimited() {
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
        )
        detector.foundOops = true
        mockLimitRate(limited = true)

        detector.finish(FakeCombinedTimeProvider.now())
        verify(exactly = 0) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseAbsoluteTime>()) }
    }

    @Test
    fun finishOopsFound() {
        detector = KernelOopsDetector(
            tokenBucketStore = mockTokenBucketStore,
            handleEventOfInterest = mockHandleEventOfInterest,
        )
        detector.foundOops = true
        mockLimitRate(limited = false)

        val time = FakeCombinedTimeProvider.now()
        detector.finish(time)
        verify(exactly = 1) { mockHandleEventOfInterest.handleEventOfInterest(any<BaseAbsoluteTime>()) }
    }
}
