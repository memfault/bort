package com.memfault.bort.dropbox

import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.tokenbucket.TokenBucket
import com.memfault.bort.tokenbucket.TokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class ClientServerFileUploadProcessorTest {
    private val marHoldingArea: MarFileHoldingArea = mockk(relaxed = true)
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
        marHoldingArea = marHoldingArea,
        tokenBucketStore = tokenBucketStore,
    )
    private var rateLimit = false

    @Test
    fun notProcessedIfRateLimited() {
        runBlocking {
            rateLimit = true
            processor.process(mockEntry(text = FILE_CONTENT.decodeToString()))
            coVerify(exactly = 0) { marHoldingArea.addSampledMarFileDirectlyFromOtherDevice(any()) }
        }
    }

    @Test
    fun processed() {
        val file = slot<File>()

        runBlocking {
            rateLimit = false
            processor.process(mockEntry(text = FILE_CONTENT.decodeToString()))
            coVerify(exactly = 1) { marHoldingArea.addSampledMarFileDirectlyFromOtherDevice(capture(file)) }
            assertArrayEquals(FILE_CONTENT, file.captured.readBytes())
        }
    }

    companion object {
        private val FILE_CONTENT = byteArrayOf(0x1, 0x2, 0x3, 0x4)
    }
}
