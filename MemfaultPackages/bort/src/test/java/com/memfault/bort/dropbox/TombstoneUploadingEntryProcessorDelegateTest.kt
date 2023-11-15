package com.memfault.bort.dropbox

import com.memfault.bort.PackageManagerClient
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class TombstoneUploadingEntryProcessorDelegateTest {
    private val packageManagerClient: PackageManagerClient = mockk(relaxed = true)
    private val tokenBucketStore: TokenBucketStore = mockk(relaxed = true)

    @Test
    fun doesNotScrub() {
        val processor =
            TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = packageManagerClient,
                tokenBucketStore = tokenBucketStore,
                tempFileFactory = TestTemporaryFileFactory,
                scrubTombstones = { false },
            )
        val inputFile = loadFile("test_tombstone_1.txt")
        val outputFile = processor.scrub(inputFile, "UPLOAD_TOMBSTONE")
        assertEquals(inputFile, outputFile)
        assertTrue(inputFile.exists())
    }

    @Test
    fun scrubsTombstoneMemoryDump1() {
        val processor =
            TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = packageManagerClient,
                tokenBucketStore = tokenBucketStore,
                tempFileFactory = TestTemporaryFileFactory,
                scrubTombstones = { true },
            )
        val inputFile = loadFile("test_tombstone_1.txt")
        val outputFile = processor.scrub(inputFile, "UPLOAD_TOMBSTONE")
        val expectedOutput = loadFile("test_tombstone_1_scrubbed.txt")
        assertEquals(expectedOutput.readText(), outputFile.readText())
        assertFalse(inputFile.exists())
    }

    @Test
    fun scrubsTombstoneMemoryDump2() {
        val processor =
            TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = packageManagerClient,
                tokenBucketStore = tokenBucketStore,
                tempFileFactory = TestTemporaryFileFactory,
                scrubTombstones = { true },
            )
        val inputFile = loadFile("test_tombstone_2.txt")
        val outputFile = processor.scrub(inputFile, "UPLOAD_TOMBSTONE")
        val expectedOutput = loadFile("test_tombstone_2_scrubbed.txt")
        assertEquals(expectedOutput.readText(), outputFile.readText())
        assertFalse(inputFile.exists())
    }

    private fun loadFile(name: String): File {
        val file = File(
            TombstoneUploadingEntryProcessorDelegateTest::class.java.getResource("/$name")!!.path,
        )
        val tempFile = File.createTempFile("tmptombstone", "temptombstone.txt")
        tempFile.deleteSilently()
        return file.copyTo(tempFile)
    }
}
