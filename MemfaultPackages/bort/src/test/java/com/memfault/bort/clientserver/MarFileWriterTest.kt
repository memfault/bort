package com.memfault.bort.clientserver

import assertk.Assert
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
            assertThat(zip.nextEntry).isNull()
        }
    }

    @Test
    fun createMarFileWithNoAttachment() {
        val manifest = heartbeat(timeMs = 123456789, filename = null)
        val marFile = createMarFile("mar.mar", manifest, fileContent = null)

        ZipInputStream(FileInputStream(marFile)).use { zip ->
            zip.assertNextEntry(marFile, manifest, fileContent = null)
            assertThat(zip.nextEntry).isNull()
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
            override val uploadRequiresBatteryNotLow: Boolean
                get() = TODO("Not used")
            override val uploadRequiresCharging: Boolean
                get() = TODO("Not used")
            override val uploadCompressionEnabled: Boolean get() = TODO("Not used")
            override val connectTimeout: Duration get() = TODO("Not used")
            override val writeTimeout: Duration get() = TODO("Not used")
            override val readTimeout: Duration get() = TODO("Not used")
            override val callTimeout: Duration get() = TODO("Not used")
            override val zipCompressionLevel: Int = 4
            override val batchMarUploads: Boolean get() = TODO("Not used")
            override val batchedMarUploadPeriod: Duration get() = TODO("Not used")
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

        assertThat(batched.size).isEqualTo(2)
        assertThat(marFile1.exists()).isFalse()
        assertThat(marFile2.exists()).isFalse()
        assertThat(marFile3.exists()).isFalse()
        val firstBatch = batched[0]
        ZipInputStream(FileInputStream(firstBatch)).use { zip ->
            zip.assertNextEntry(firstBatch, manifest1, FILE_CONTENT)
            zip.assertNextEntry(firstBatch, manifest2, FILE_CONTENT_2)
            assertThat(zip.nextEntry).isNull()
        }
        val secondBatch = batched[1]
        ZipInputStream(FileInputStream(secondBatch)).use { zip ->
            zip.assertNextEntry(secondBatch, manifest3, FILE_CONTENT_3)
            assertThat(zip.nextEntry).isNull()
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
        assertThat(elements.chunkByElementSize(10) { it.toLong() }).isEqualTo(expected)
    }

    @Test
    fun fileMissingThrowsException() {
        var marFile = File.createTempFile("marfile", ".mar")

        val inputFile = File("/this/does/not/exist.bin")
        val manifest = heartbeat(timeMs = 123456789)
        val marFileWriter = MarFileWriter
        assertFailure {
            marFileWriter.writeMarFile(marFile, manifest, inputFile, 0)
        }.isInstanceOf<FileMissingException>()
    }

    private fun ZipInputStream.assertNextEntry(
        marFile: File,
        manifest: MarManifest,
        fileContent: String?,
    ) {
        val dirEntry = nextEntry
        assertThat(dirEntry.isDirectory).isTrue()
        assertThat(dirEntry.name.startsWith(marFile.name)).isTrue()

        val manifestEntry = nextEntry
        assertThat(manifestEntry.name.endsWith("manifest.json")).isTrue()
        val manifestOutputString = readBytes().decodeToString()
        val manifestOutput = BortJson.decodeFromString(MarManifest.serializer(), manifestOutputString)
        assertThat(manifestOutput).isEqualTo(manifest)

        val filename = manifest.filename()
        assertThat(filename == null).isEqualTo(fileContent == null)
        filename?.let {
            val fileEntry = nextEntry
            assertThat(fileEntry.name.endsWith(filename)).isTrue()
            val fileContentOutput = readBytes().decodeToString()
            assertThat(fileContentOutput).isEqualTo(fileContent)
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
                    reportType = "heartbeat",
                    reportName = null,
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
