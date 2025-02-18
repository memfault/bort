package com.memfault.bort.dropbox

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class ClientServerFileUploadProcessorTest {
    private val marHoldingArea: MarFileHoldingArea = mockk(relaxed = true)
    private val tokenBucketStore = mockk<TokenBucketStore> {
        every { takeSimple(any(), any(), any()) } answers { !rateLimit }
    }
    private val processor = ClientServerFileUploadProcessor(
        tempFileFactory = TestTemporaryFileFactory,
        marHoldingArea = marHoldingArea,
        tokenBucketStore = tokenBucketStore,
    )
    private var rateLimit = false

    @Test
    fun notProcessedIfRateLimited() = runTest {
        rateLimit = true
        processor.process(mockEntry(text = FILE_CONTENT.decodeToString()))
        coVerify(exactly = 0) { marHoldingArea.addSampledMarFileDirectlyFromOtherDevice(any()) }
    }

    @Test
    fun processed() = runTest {
        val file = slot<File>()

        rateLimit = false
        processor.process(mockEntry(text = FILE_CONTENT.decodeToString()))
        coVerify(exactly = 1) { marHoldingArea.addSampledMarFileDirectlyFromOtherDevice(capture(file)) }
        assertThat(file.captured.readBytes()).isEqualTo(FILE_CONTENT)
    }

    companion object {
        private val FILE_CONTENT = byteArrayOf(0x1, 0x2, 0x3, 0x4)
    }
}
