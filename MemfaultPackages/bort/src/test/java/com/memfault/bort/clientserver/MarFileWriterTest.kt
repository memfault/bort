package com.memfault.bort.clientserver

import assertk.Assert
import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isNull
import assertk.assertions.support.expected
import com.memfault.bort.BortJson
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.MarFileWriter.Companion.MAR_SIZE_TOLERANCE_BYTES
import com.memfault.bort.clientserver.MarFileWriter.Companion.chunkByElementSize
import com.memfault.bort.clientserver.MarFileWriter.Companion.writeBatchedMarFile
import com.memfault.bort.clientserver.MarFileWriter.Companion.writeMarFile
import com.memfault.bort.clientserver.MarFileWriterTest.Companion.filename
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.NetworkConstraint
import com.memfault.bort.settings.Resolution
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.time.Duration

internal class MarFileWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

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
    fun createMarFileWithNoAttachment() {
        val manifest = heartbeat(timeMs = 123456789, filename = null)
        val marFile = createMarFile("mar.mar", manifest, fileContent = null)

        ZipInputStream(FileInputStream(marFile)).use { zip ->
            zip.assertNextEntry(marFile, manifest, fileContent = null)
            assertNull(zip.nextEntry)
        }
    }

    @Test
    fun mergeMarFiles() {
        val manifest1 = heartbeat(timeMs = 123456789)
        val marFile1 = createMarFile("mar1.mar", manifest1, FILE_CONTENT)

        val manifest2 = logcat(timeMs = 987654321)
        val marFile2 = createMarFile("mar2.mar", manifest2, FILE_CONTENT_2)

        assertThat(marFile1).exists()
        assertThat(marFile2).exists()

        val batchedMarFile = File.createTempFile("marfile", "batched.mar")
        writeBatchedMarFile(batchedMarFile, listOf(marFile1, marFile2), compressionLevel = 4)

        assertThat(marFile1).doesNotExist()
        assertThat(marFile2).doesNotExist()
        FileInputStream(batchedMarFile).use { fileIn ->
            ZipInputStream(fileIn).use { zipIn ->
                zipIn.assertNextEntry(batchedMarFile, manifest1, FILE_CONTENT)
                zipIn.assertNextEntry(batchedMarFile, manifest2, FILE_CONTENT_2)
                assertThat(zipIn.nextEntry).isNull()
            }
        }
    }

    @Test
    fun mergeMarFiles_oneEmpty() {
        val manifest1 = heartbeat(timeMs = 123456789)
        val marFile1 = createMarFile("mar1.mar", manifest1, FILE_CONTENT)

        val marFile2 = folder.newFile("mar2.mar")

        assertThat(marFile1).exists()
        assertThat(marFile2).exists()

        val batchedMarFile = File.createTempFile("marfile", "batched.mar")
        writeBatchedMarFile(batchedMarFile, listOf(marFile1, marFile2), compressionLevel = 4)

        assertThat(marFile1).doesNotExist()
        assertThat(marFile2).doesNotExist()
        FileInputStream(batchedMarFile).use { fileIn ->
            ZipInputStream(fileIn).use { zipIn ->
                zipIn.assertNextEntry(batchedMarFile, manifest1, FILE_CONTENT)
                assertThat(zipIn.nextEntry).isNull()
            }
        }
    }

    @Test
    fun mergeMarFiles_oneCorrupted() {
        val manifest1 = heartbeat(timeMs = 123456789)
        val marFile1 = createMarFile("mar1.mar", manifest1, FILE_CONTENT)

        val marFile2 = createMarFile("mar1.mar", manifest1, FILE_CONTENT).also { marFile ->
            RandomAccessFile(marFile, "rw").use { raf ->
                raf.channel.truncate(10)
            }
        }

        assertThat(marFile1).exists()
        assertThat(marFile2).exists()

        val batchedMarFile = File.createTempFile("marfile", "batched.mar")
        writeBatchedMarFile(batchedMarFile, listOf(marFile1, marFile2), compressionLevel = 4)

        assertThat(marFile1).doesNotExist()
        assertThat(marFile2).doesNotExist()
        FileInputStream(batchedMarFile).use { fileIn ->
            ZipInputStream(fileIn).use { zipIn ->
                zipIn.assertNextEntry(batchedMarFile, manifest1, FILE_CONTENT)
                assertThat(zipIn.nextEntry).isNull()
            }
        }
    }

    @Test
    fun mergeToMultipleFiles() {
        val settings = object : HttpApiSettings {
            override val projectKey: String get() = "key"
            override val filesBaseUrl: String get() = TODO("Not used")
            override val deviceBaseUrl: String get() = TODO("Not used")
            override val uploadNetworkConstraint: NetworkConstraint get() = TODO("Not used")
            override val uploadCompressionEnabled: Boolean get() = TODO("Not used")
            override val connectTimeout: Duration get() = TODO("Not used")
            override val writeTimeout: Duration get() = TODO("Not used")
            override val readTimeout: Duration get() = TODO("Not used")
            override val callTimeout: Duration get() = TODO("Not used")
            override val zipCompressionLevel: Int = 4
            override val batchMarUploads: Boolean get() = TODO("Not used")
            override val batchedMarUploadPeriod: Duration get() = TODO("Not used")
            override suspend fun useDeviceConfig(): Boolean = TODO("Not used")
            override val deviceConfigInterval: Duration get() = TODO("Not used")
            override val maxMarFileSizeBytes: Int get() = 3000 + MAR_SIZE_TOLERANCE_BYTES
            override val maxMarStorageBytes: Long get() = TODO("Not used")
            override val maxMarUnsampledStoredAge: Duration get() = TODO("Not used")
            override val maxMarUnsampledStoredBytes: Long get() = TODO("Not used")
        }
        val temp: TemporaryFileFactory = TestTemporaryFileFactory
        val writer = MarFileWriter(settings, temp, { 4 })

        // file size: 1290
        val manifest1 = heartbeat(timeMs = 123456789)
        val marFile1 = createMarFile("mar1.mar", manifest1, FILE_CONTENT)

        // file size: 1326
        val manifest2 = heartbeat(timeMs = 123456789)
        val marFile2 = createMarFile("mar2.mar", manifest2, FILE_CONTENT_2)

        // file size: 1310
        val manifest3 = logcat(timeMs = 987654321)
        val marFile3 = createMarFile("mar3.mar", manifest3, FILE_CONTENT_3)

        // Batch with a 3000 byte limit (configured in settings above).
        val batched = writer.batchMarFiles(listOf(marFile1, marFile2, marFile3))

        assertEquals(2, batched.size)
        assertFalse(marFile1.exists())
        assertFalse(marFile2.exists())
        assertFalse(marFile3.exists())
        val firstBatch = batched[0]
        ZipInputStream(FileInputStream(firstBatch)).use { zip ->
            zip.assertNextEntry(firstBatch, manifest1, FILE_CONTENT)
            zip.assertNextEntry(firstBatch, manifest2, FILE_CONTENT_2)
            assertNull(zip.nextEntry)
        }
        val secondBatch = batched[1]
        ZipInputStream(FileInputStream(secondBatch)).use { zip ->
            zip.assertNextEntry(secondBatch, manifest3, FILE_CONTENT_3)
            assertNull(zip.nextEntry)
        }
    }

    @Test
    fun chunkByElementSize() {
        val elements = listOf(11, 5, 5, 10, 11, 5, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        val expected = listOf(
            listOf(11),
            listOf(5, 5),
            listOf(10),
            listOf(11),
            listOf(5, 4, 1),
            listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            listOf(1),
        )
        assertEquals(expected, elements.chunkByElementSize(10) { it.toLong() })
    }

    @Test
    fun fileMissingThrowsException() {
        var marFile = File.createTempFile("marfile", ".mar")

        val inputFile = File("/this/does/not/exist.bin")
        val manifest = heartbeat(timeMs = 123456789)
        val marFileWriter = MarFileWriter
        assertThrows<FileMissingException> {
            marFileWriter.writeMarFile(marFile, manifest, inputFile, 0)
        }
    }

    private fun ZipInputStream.assertNextEntry(
        marFile: File,
        manifest: MarManifest,
        fileContent: String?,
    ) {
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
        const val FILE_CONTENT = "hi this is the input file"
        private const val FILE_CONTENT_2 = "and this is the second input file with some slightly different content"
        private const val FILE_CONTENT_3 = "blah blah blah blah blah blah blah blah blah blah blah blah blah blah " +
            "blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah "
        val device = MarDevice(
            projectKey = "projectKey",
            hardwareVersion = "hardwareVersion",
            softwareVersion = "softwareVersion",
            softwareType = "softwareType",
            deviceSerial = "deviceSerial",
        )

        fun time(timeMs: Long) = CombinedTime(
            uptime = Duration.ZERO.boxed(),
            elapsedRealtime = Duration.ZERO.boxed(),
            linuxBootId = "bootid",
            bootCount = 1,
            timestamp = Instant.ofEpochMilli(timeMs),
        )

        fun heartbeat(
            timeMs: Long,
            filename: String? = "batterystats",
            resolution: Resolution = Resolution.NORMAL,
        ) =
            MarManifest(
                collectionTime = time(timeMs),
                type = "android-heartbeat",
                device = device,
                metadata = MarMetadata.HeartbeatMarMetadata(
                    batteryStatsFileName = filename,
                    heartbeatIntervalMs = 2,
                    customMetrics = emptyMap(),
                    builtinMetrics = emptyMap(),
                ),
                debuggingResolution = Resolution.NOT_APPLICABLE,
                loggingResolution = Resolution.NOT_APPLICABLE,
                monitoringResolution = resolution,
            )

        fun logcat(timeMs: Long, resolution: Resolution = Resolution.NORMAL) = MarManifest(
            collectionTime = time(timeMs),
            type = "android-logcat",
            device = device,
            metadata = MarMetadata.LogcatMarMetadata(
                logFileName = "logcat",
                command = emptyList(),
                cid = LogcatCollectionId(UUID.randomUUID()),
                nextCid = LogcatCollectionId(UUID.randomUUID()),
            ),
            debuggingResolution = resolution,
            loggingResolution = Resolution.NORMAL,
            monitoringResolution = Resolution.NOT_APPLICABLE,
        )

        private fun MarManifest.filename() = when (val meta = metadata) {
            is MarMetadata.HeartbeatMarMetadata -> meta.batteryStatsFileName
            is MarMetadata.LogcatMarMetadata -> meta.logFileName
            else -> throw IllegalArgumentException("Unsupported in test: $this")
        }

        fun createMarFile(name: String, manifest: MarManifest, fileContent: String?): File {
            val file = File.createTempFile("marfile", name)
            val inputFile = fileContent?.let {
                File.createTempFile("input", manifest.filename()).also {
                    it.writeText(fileContent)
                }
            }
            writeMarFile(file, manifest, inputFile, compressionLevel = 4)
            return file
        }
    }
}

private fun Assert<File>.doesNotExist() = given { actual ->
    if (!actual.exists()) return
    expected("to not exist")
}
