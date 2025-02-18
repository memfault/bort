package com.memfault.bort.dropbox

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.mockk
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
                useNativeCrashTombstones = { false },
                operationalCrashesExclusions = { emptyList() },
            )
        val inputFile = loadFile("test_tombstone_1.txt")
        val outputFile = processor.scrub(inputFile, "UPLOAD_TOMBSTONE")
        assertThat(outputFile).isEqualTo(inputFile)
        assertThat(inputFile.exists()).isTrue()
    }

    @Test
    fun scrubsTombstoneMemoryDump1() {
        val processor =
            TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = packageManagerClient,
                tokenBucketStore = tokenBucketStore,
                tempFileFactory = TestTemporaryFileFactory,
                scrubTombstones = { true },
                useNativeCrashTombstones = { false },
                operationalCrashesExclusions = { emptyList() },
            )
        val inputFile = loadFile("test_tombstone_1.txt")
        val outputFile = processor.scrub(inputFile, "UPLOAD_TOMBSTONE")
        val expectedOutput = loadFile("test_tombstone_1_scrubbed.txt")
        assertThat(outputFile.readText()).isEqualTo(expectedOutput.readText())
        assertThat(inputFile.exists()).isFalse()
    }

    @Test
    fun scrubsTombstoneMemoryDump2() {
        val processor =
            TombstoneUploadingEntryProcessorDelegate(
                packageManagerClient = packageManagerClient,
                tokenBucketStore = tokenBucketStore,
                tempFileFactory = TestTemporaryFileFactory,
                scrubTombstones = { true },
                useNativeCrashTombstones = { false },
                operationalCrashesExclusions = { emptyList() },
            )
        val inputFile = loadFile("test_tombstone_2.txt")
        val outputFile = processor.scrub(inputFile, "UPLOAD_TOMBSTONE")
        val expectedOutput = loadFile("test_tombstone_2_scrubbed.txt")
        assertThat(outputFile.readText()).isEqualTo(expectedOutput.readText())
        assertThat(inputFile.exists()).isFalse()
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
