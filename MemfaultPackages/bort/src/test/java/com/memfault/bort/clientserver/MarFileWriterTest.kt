package com.memfault.bort.clientserver

import com.memfault.bort.BortJson
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.clientserver.MarFileWriter.Companion.writeBatchedMarFile
import com.memfault.bort.clientserver.MarFileWriter.Companion.writeMarFile
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.time.Duration
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

internal class MarFileWriterTest {
    @Test
    fun createMarFile() {
        val manifest = heartbeat(timeMs = 123456789)
        val marFile = createMarFile("mar.mar", manifest, FILE_CONTENT)

        ZipInputStream(FileInputStream(marFile)).use { zip ->
            zip.assertNextEntry(marFile, manifest, FILE_CONTENT)
            assertNull(zip.nextEntry)
        }
    }

    @Test
    fun mergeMarFiles() {
        val manifest1 = heartbeat(timeMs = 123456789)
        val marFile1 = createMarFile("mar1.mar", manifest1, FILE_CONTENT)

        val manifest2 = logcat(timeMs = 987654321)
        val marFile2 = createMarFile("mar2.mar", manifest2, FILE_CONTENT_2)

        val batchedMarFile = File.createTempFile("marfile", "batched.mar")
        writeBatchedMarFile(batchedMarFile, listOf(marFile1, marFile2))

        assertFalse(marFile1.exists())
        assertFalse(marFile2.exists())
        ZipInputStream(FileInputStream(batchedMarFile)).use { zip ->
            zip.assertNextEntry(batchedMarFile, manifest1, FILE_CONTENT)
            zip.assertNextEntry(batchedMarFile, manifest2, FILE_CONTENT_2)
            assertNull(zip.nextEntry)
        }
    }

    private fun ZipInputStream.assertNextEntry(marFile: File, manifest: MarManifest, fileContent: String?) {
        val dirEntry = nextEntry
        assertTrue(dirEntry.isDirectory)
        assertTrue(dirEntry.name.startsWith(marFile.name))

        val manifestEntry = nextEntry
        assertTrue(manifestEntry.name.endsWith("manifest.json"))
        val manifestOutputString = readBytes().decodeToString()
        val manifestOutput = BortJson.decodeFromString(MarManifest.serializer(), manifestOutputString)
        assertEquals(manifest, manifestOutput)

        val filename = manifest.filename()
        assertEquals(fileContent == null, filename == null)
        filename?.let {
            val fileEntry = nextEntry
            assertTrue(fileEntry.name.endsWith(filename))
            val fileContentOutput = readBytes().decodeToString()
            assertEquals(fileContent, fileContentOutput)
        }
    }

    companion object {
        private const val FILE_CONTENT = "hi this is the input file"
        private const val FILE_CONTENT_2 = "and this is the second input file"
        private val device = MarDevice(
            projectKey = "projectKey",
            hardwareVersion = "hardwareVersion",
            softwareVersion = "softwareVersion",
            softwareType = "softwareType",
            deviceSerial = "deviceSerial",
        )

        private fun time(timeMs: Long) = CombinedTime(
            uptime = Duration.ZERO.boxed(),
            elapsedRealtime = Duration.ZERO.boxed(),
            linuxBootId = "bootid",
            bootCount = 1,
            timestamp = Instant.ofEpochMilli(timeMs)
        )

        private fun heartbeat(timeMs: Long) = MarManifest(
            collectionTime = time(timeMs),
            type = "android-heartbeat",
            device = device,
            metadata = MarMetadata.HeartbeatMarMetadata(
                batteryStatsFileName = "batterystats",
                heartbeatIntervalMs = 2,
                customMetrics = emptyMap(),
                builtinMetrics = emptyMap(),
            )
        )

        private fun logcat(timeMs: Long) = MarManifest(
            collectionTime = time(timeMs),
            type = "android-logcat",
            device = device,
            metadata = MarMetadata.LogcatMarMetadata(
                logFileName = "logcat",
                command = emptyList(),
                cid = LogcatCollectionId(UUID.randomUUID()),
                nextCid = LogcatCollectionId(UUID.randomUUID()),
            )
        )

        private fun MarManifest.filename() = when (val meta = metadata) {
            is MarMetadata.HeartbeatMarMetadata -> meta.batteryStatsFileName
            is MarMetadata.LogcatMarMetadata -> meta.logFileName
            else -> throw IllegalArgumentException("Unsupported in test: $this")
        }

        private fun createMarFile(name: String, manifest: MarManifest, fileContent: String?): File {
            val file = File.createTempFile("marfile", name)
            val inputFile = fileContent?.let {
                File.createTempFile("input", manifest.filename()).also {
                    it.writeText(fileContent)
                }
            }
            writeMarFile(file, manifest, inputFile)
            return file
        }
    }
}
