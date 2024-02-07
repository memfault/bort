package com.memfault.bort.boot

import com.memfault.bort.DEV_MODE_DISABLED
import com.memfault.bort.DumpsterClient
import com.memfault.bort.tokenbucket.MockTokenBucketFactory
import com.memfault.bort.tokenbucket.MockTokenBucketStorage
import com.memfault.bort.tokenbucket.RealTokenBucketStore
import com.memfault.bort.tokenbucket.StoredTokenBucketMap
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

private const val TEST_BUCKET_CAPACITY = 5

class RebootEventUploaderTest {
    @Test
    fun rateLimit() = runTest {
        val enqueueUpload = mockk<EnqueueUpload>(relaxed = true) {
            every { enqueue(any(), any(), any()) } returns Unit
        }

        val dumpsterClient = mockk<DumpsterClient> {
            coEvery { getprop() } returns mapOf(AndroidBootReason.SYS_BOOT_REASON_KEY to "alarm")
        }

        val uploader = RebootEventUploader(
            dumpsterClient = dumpsterClient,
            tokenBucketStore = RealTokenBucketStore(
                storage = MockTokenBucketStorage(StoredTokenBucketMap()),
                getMaxBuckets = { 1 },
                getTokenBucketFactory = {
                    MockTokenBucketFactory(
                        defaultCapacity = TEST_BUCKET_CAPACITY,
                        defaultPeriod = 1.milliseconds,
                    )
                },
                devMode = DEV_MODE_DISABLED,
            ),
            linuxBootId = { "bootid" },
            enqueueUpload = enqueueUpload,
        )

        repeat(15) {
            uploader.handleUntrackedBootCount(1)
        }

        verify(exactly = TEST_BUCKET_CAPACITY) { enqueueUpload.enqueue(any(), any(), any()) }
    }
}
