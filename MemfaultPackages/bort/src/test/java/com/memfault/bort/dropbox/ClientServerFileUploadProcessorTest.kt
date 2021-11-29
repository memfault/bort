package com.memfault.bort.dropbox

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.tokenbucket.TokenBucket
import com.memfault.bort.tokenbucket.TokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ClientServerFileUploadProcessorTest {
    private val mockEnqueueUpload: EnqueueUpload = mockk(relaxed = true)
    private val tokenBucketStore = mockk<TokenBucketStore> {
        val block = slot<(map: TokenBucketMap) -> Any>()

        every { edit(capture(block)) } answers {
            val map = mockk<TokenBucketMap>()
            val bucket = mockk<TokenBucket>()
            every {
                bucket.take(any(), tag = "mar_file")
            } answers { !rateLimit }
            every {
                map.upsertBucket(any(), any(), any())
            } returns bucket
            block.captured(map)
        }
    }
    private val processor = ClientServerFileUploadProcessor(
        tempFileFactory = TestTemporaryFileFactory,
        enqueueUpload = mockEnqueueUpload,
        deviceInfoProvider = FakeDeviceInfoProvider(),
        combinedTimeProvider = FakeCombinedTimeProvider,
        tokenBucketStore = tokenBucketStore,
    )
    private var rateLimit = false

    @Test
    fun notProcessedIfRateLimited() {
        runBlocking {
            rateLimit = true
            processor.process(mockEntry(text = FILE_CONTENT.decodeToString()))
            verify(exactly = 0) { mockEnqueueUpload.enqueue(any(), any(), any(), any()) }
        }
    }

    @Test
    fun processed() {
        val file = slot<File>()
        val metadata = slot<FileUploadPayload>()
        val time = slot<CombinedTime>()

        runBlocking {
            rateLimit = false
            processor.process(mockEntry(text = FILE_CONTENT.decodeToString()))
            verify(exactly = 1) { mockEnqueueUpload.enqueue(capture(file), capture(metadata), any(), capture(time)) }
            assertArrayEquals(FILE_CONTENT, file.captured.readBytes())
            assertInstanceOf(MarFileUploadPayload::class.java, metadata.captured)
            assertEquals(FakeCombinedTimeProvider.now(), time.captured)
        }
    }

    companion object {
        private val FILE_CONTENT = byteArrayOf(0x1, 0x2, 0x3, 0x4)
    }
}
