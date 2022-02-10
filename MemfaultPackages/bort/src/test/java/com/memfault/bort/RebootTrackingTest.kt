package com.memfault.bort

import com.memfault.bort.tokenbucket.MockTokenBucketFactory
import com.memfault.bort.tokenbucket.MockTokenBucketStorage
import com.memfault.bort.tokenbucket.StoredTokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.milliseconds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class RebootTrackingTest {
    @Test
    fun testIfBootNotTrackedYet() {
        val currentBootCount = 1
        val untrackedBootCountHandler: (Int) -> Unit = mockk {
            every { this@mockk(any()) } returns Unit
        }
        val provider = object : LastTrackedBootCountProvider {
            override var bootCount: Int = 0
        }

        assertNotEquals(currentBootCount, provider.bootCount)
        BootCountTracker(provider, untrackedBootCountHandler).trackIfNeeded(currentBootCount)

        // Expect the LastTrackedBootCountProvider to have been updated and the block to have been called
        assertEquals(currentBootCount, provider.bootCount)
        verify(exactly = 1) {
            untrackedBootCountHandler(any())
        }

        // Block should not be called again, because the boot count has not been bumped
        BootCountTracker(provider, untrackedBootCountHandler).trackIfNeeded(currentBootCount)
        verify(exactly = 1) {
            untrackedBootCountHandler(any())
        }
    }
}

class AndroidBootReasonParsing {
    @Test
    fun parsing() {
        val testCases = listOf(
            null to AndroidBootReason("reboot", "bort_unknown"),
            "" to AndroidBootReason(""),
            "reboot" to AndroidBootReason("reboot"),
            "reboot,userrequested" to AndroidBootReason("reboot", "userrequested"),
            "shutdown,battery,thermal" to AndroidBootReason("shutdown", "battery", listOf("thermal")),
            "shutdown,battery,thermal,50C" to AndroidBootReason("shutdown", "battery", listOf("thermal", "50C"))
        )
        for ((input, expectedOutput) in testCases) {
            assertEquals(expectedOutput, AndroidBootReason.parse(input))
        }
    }
}

private const val TEST_BUCKET_CAPACITY = 5

class RebootEventUploaderTest {
    @Test
    fun rateLimit() {
        val enqueueUpload = mockk<EnqueueUpload>(relaxed = true) {
            every { enqueue(any(), any()) } returns Unit
        }

        val uploader = RebootEventUploader(
            deviceInfo = runBlocking { FakeDeviceInfoProvider().getDeviceInfo() },
            androidSysBootReason = "alarm",
            tokenBucketStore = TokenBucketStore(
                storage = MockTokenBucketStorage(StoredTokenBucketMap()),
                getMaxBuckets = { 1 },
                getTokenBucketFactory = {
                    MockTokenBucketFactory(
                        defaultCapacity = TEST_BUCKET_CAPACITY,
                        defaultPeriod = 1.milliseconds,
                    )
                },
            ),
            getLinuxBootId = { "bootid" },
            enqueueUpload = enqueueUpload,
        )

        repeat(15) {
            uploader.handleUntrackedBootCount(1)
        }

        verify(exactly = TEST_BUCKET_CAPACITY) { enqueueUpload.enqueue(any(), any()) }
    }
}
