package com.memfault.bort.clientserver

import com.memfault.bort.BortJson
import com.memfault.bort.settings.Resolution.HIGH
import com.memfault.bort.settings.Resolution.NORMAL
import com.memfault.bort.settings.SamplingConfig
import java.io.File
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class MarUnsampledHoldingAreaTest {
    @get:Rule
    private val tempFolder = TemporaryFolder.builder().assureDeletion().build()
    private lateinit var unsampledHoldingDirectory: File

    @Before
    fun setup() {
        tempFolder.create()
        unsampledHoldingDirectory = tempFolder.newFolder("unsampled")
    }

    @Test
    fun addAndRemove() {
        val holdingArea = MarUnsampledHoldingArea(unsampledHoldingDirectory)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, MarFileWriterTest.FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        assertTrue(holdingArea.eligibleForUpload(SamplingConfig()).isEmpty())

        holdingArea.add(marFileWithManifest)

        // Not returned when config isn't high enough.
        assertTrue(holdingArea.eligibleForUpload(SamplingConfig()).isEmpty())

        // Run cleanup (no-op)
        holdingArea.cleanup(limitBytes = 999_999_999, Duration.ZERO)

        val eligible = holdingArea.eligibleForUpload(SamplingConfig(monitoringResolution = HIGH))
        assertEquals(1, eligible.size)
        val first = eligible.first()
        assertEquals(manifest, first.manifest)
    }

    @Test
    fun cleanup_deletesOrphanManifest() {
        val holdingArea = MarUnsampledHoldingArea(unsampledHoldingDirectory)
        val manifest1 = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = NORMAL)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, MarFileWriterTest.FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        holdingArea.add(marFileWithManifest1)
        // Wait before creating the second file, to ensure that it has a later modification timestamp.
        Thread.sleep(5)
        val manifest2 = MarFileWriterTest.heartbeat(timeMs = 223456789, resolution = NORMAL)
        val marFile2 = MarFileWriterTest.createMarFile("mar2.mar", manifest2, MarFileWriterTest.FILE_CONTENT)
        val marFileWithManifest2 = MarFileWithManifest(marFile = marFile2, manifest = manifest2)
        val marFile2Size = marFile2.length()
        holdingArea.add(marFileWithManifest2)

        assertTrue(unsampledHoldingDirectory.listFiles().size == 4)

        // Run cleanup - will delete the mar file (over limit), and should also cleanup the manifest file.
        val manifest1Size = BortJson.encodeToString(MarManifest.serializer(), manifest1).length
        val manifest2Size = BortJson.encodeToString(MarManifest.serializer(), manifest2).length
        holdingArea.cleanup(limitBytes = marFile2Size + manifest1Size + manifest2Size, Duration.ZERO)

        assertTrue(unsampledHoldingDirectory.listFiles().size == 2)
        val eligible = holdingArea.eligibleForUpload(SamplingConfig(monitoringResolution = HIGH))
        assertEquals(1, eligible.size)
        val first = eligible.first()
        assertEquals(manifest2, first.manifest)
    }

    @Test
    fun cleanup_deletesOrphanMarFile() {
        val holdingArea = MarUnsampledHoldingArea(unsampledHoldingDirectory)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = NORMAL)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, MarFileWriterTest.FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.add(marFileWithManifest)

        // Run cleanup (no-op)
        holdingArea.cleanup(limitBytes = 999_999_999, Duration.ZERO)

        // Delete the mar file from the holding area
        val manifestFileInHoldingArea =
            holdingArea.manifestFileForMar(holdingArea.eligibleForUpload(SamplingConfig()).first().marFile)
        manifestFileInHoldingArea.delete()
        assertTrue(holdingArea.eligibleForUpload(SamplingConfig()).isEmpty())

        // Run cleanup
        holdingArea.cleanup(limitBytes = 999_999_999, Duration.ZERO)

        assertTrue(unsampledHoldingDirectory.listFiles().size == 0)
    }
}
