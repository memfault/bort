package com.memfault.bort.clientserver

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.clientserver.MarFileWriterTest.Companion.FILE_CONTENT
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.Resolution
import com.memfault.bort.settings.Resolution.HIGH
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.ClientServerMode.CLIENT
import com.memfault.bort.shared.ClientServerMode.DISABLED
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class MarFileHoldingAreaTest {
    private val combinedTimeProvider = FakeCombinedTimeProvider

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()
    private lateinit var sampledHoldingDirectory: File
    private val deviceConfigManifest = deviceConfig(timeMs = 8877887788)
    private val deviceConfigMarFile = MarFileWriterTest.createMarFile("mar1.mar", deviceConfigManifest, null)
    private val deviceConfigMar = MarFileWithManifest(
        marFile = deviceConfigMarFile,
        manifest = deviceConfigManifest,
    )
    private val marFileWriter = mockk<MarFileWriter>(relaxed = true) {
        every { createMarFile(null, any()) } returns Result.success(deviceConfigMar)
    }
    private val oneTimeMarUpload = mockk<OneTimeMarUpload>(relaxed = true)
    private val currentSamplingConfig = mockk<CurrentSamplingConfig> { coEvery { get() } returns SamplingConfig() }
    private val linkedDeviceFileSender = mockk<LinkedDeviceFileSender>(relaxed = true)
    private val eligibleForUpload = mutableListOf<MarFileWithManifest>()
    private val unsampledHoldingArea = mockk<MarUnsampledHoldingArea>(relaxed = true) {
        every { eligibleForUpload(any()) } returns eligibleForUpload
        every { storageUsedBytes() } answers { unsampledHoldingAreaUsedBytes }
        every { oldestFileUpdatedTimestampMs() } answers { unsampledHoldingOldestFileUpdatedMs }
    }
    private var maxMarStorageBytes: Long = 999_999_999
    private var maxMarUnsampledAge: Duration = Duration.ZERO
    private var maxMarUnsampledBytes: Long = 999_999_999
    private var unsampledHoldingAreaUsedBytes: Long = 0
    private var unsampledHoldingOldestFileUpdatedMs: Long = 0

    @Before
    fun setup() {
        eligibleForUpload.clear()
        sampledHoldingDirectory = tempFolder.newFolder("sampled")
    }

    @Test
    fun sampled_immediate() {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)
        runTest {
            holdingArea.addMarFile(marFileWithManifest)
            coVerify(exactly = 1) { oneTimeMarUpload.uploadMarFile(marFile) }
            assertMarFileInHoldingArea(sampled = false, unsampled = false, marFile = marFileWithManifest)
            coVerify(exactly = 0) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = marFile,
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
        }
    }

    @Test
    fun sampled_batched() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)
        runTest {
            holdingArea.addMarFile(marFileWithManifest)
            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest)
            coVerify(exactly = 0) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = marFile,
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
        }
    }

    @Test
    fun sampled_clientServer() {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = CLIENT)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)
        runTest {
            holdingArea.addMarFile(marFileWithManifest)
            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }
            assertMarFileInHoldingArea(sampled = false, unsampled = false, marFile = marFileWithManifest)
            coVerify(exactly = 1) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = marFile,
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
        }
    }

    @Test
    fun unsampled_stored() {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)
        runTest {
            holdingArea.addMarFile(marFileWithManifest)
            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }
            assertMarFileInHoldingArea(sampled = false, unsampled = true, marFile = marFileWithManifest)
            coVerify(exactly = 0) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = marFile,
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
        }
    }

    @Test
    fun unsampled_clientServer() {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = CLIENT)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)
        runTest {
            holdingArea.addMarFile(marFileWithManifest)
            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }
            assertMarFileInHoldingArea(sampled = false, unsampled = true, marFile = marFileWithManifest)
            coVerify(exactly = 0) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = marFile,
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
        }
    }

    @Test
    fun moveFromUnsampled() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.logcat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        // Add file to the "unsampled" holding area's "eligible for upload" queue.
        eligibleForUpload.add(marFileWithManifest)

        runTest {
            holdingArea.handleSamplingConfigChange(SamplingConfig(revision = NEW_REVISION, loggingResolution = HIGH))
            // File was moved from unsampled -> sampled.
            verify(exactly = 1) { unsampledHoldingArea.removeManifest(marFileWithManifest) }
            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest)
            coVerify(exactly = 0) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = marFile,
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
            verify(exactly = 1) { oneTimeMarUpload.batchAndUpload() }
            // Additional mar file was created + added (to sampled) with handled revision.
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = deviceConfigMar)
        }
    }

    @Test
    fun moveFromUnsampled_noEligibleFiles() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        runTest {
            holdingArea.handleSamplingConfigChange(SamplingConfig(revision = NEW_REVISION, loggingResolution = HIGH))
            // File was moved from unsampled -> sampled.
            verify(exactly = 0) { unsampledHoldingArea.removeManifest(any()) }
            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(any()) }
            coVerify(exactly = 0) {
                linkedDeviceFileSender.sendFileToLinkedDevice(
                    file = any(),
                    dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
                )
            }
            verify(exactly = 0) { oneTimeMarUpload.batchAndUpload() }
        }
    }

    @Test
    fun cleanupSampledArea() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        // Wait before creating the second file, to ensure that it has a later modification timestamp.
        Thread.sleep(5)
        val manifest2 = MarFileWriterTest.logcat(timeMs = 222222222)
        val marFile2 = MarFileWriterTest.createMarFile("mar2.mar", manifest2, FILE_CONTENT)
        val marFileWithManifest2 = MarFileWithManifest(marFile = marFile2, manifest = manifest2)
        val marFile2Size = marFile2.length()

        runTest {
            holdingArea.addMarFile(marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest1)
            clearMocks(unsampledHoldingArea)

            maxMarStorageBytes = marFile2Size
            holdingArea.addMarFile(marFileWithManifest2)

            coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(any()) }
            assertMarFileInHoldingArea(sampled = false, unsampled = false, marFile = marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest2)
        }
    }

    @Test
    fun cleanupUnsampledArea() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        val marFile1Size = marFile1.length()

        unsampledHoldingAreaUsedBytes = 5000
        val offset = 5
        maxMarStorageBytes = marFile1Size + offset

        runTest {
            holdingArea.addMarFile(marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest1)
            verify { unsampledHoldingArea.cleanup(offset.toLong(), maxMarUnsampledAge) }
        }
    }

    @Test
    fun cleanupUnsampledAreaForMaxAge() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        val marFile1Size = marFile1.length()

        maxMarUnsampledAge = 1.hours
        unsampledHoldingOldestFileUpdatedMs =
            combinedTimeProvider.now().timestamp.toEpochMilli() - maxMarUnsampledAge.inWholeMilliseconds - 1

        runTest {
            holdingArea.addMarFile(marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest1)
            verify { unsampledHoldingArea.cleanup(maxMarStorageBytes - marFile1Size, maxMarUnsampledAge) }
        }
    }

    @Test
    fun cleanupUnsampledAreaForMaxBytes() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarUnsampledBytes = 1
        unsampledHoldingAreaUsedBytes = 2

        runTest {
            holdingArea.addMarFile(marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest1)
            verify { unsampledHoldingArea.cleanup(maxMarUnsampledBytes, maxMarUnsampledAge) }
        }
    }

    @Test
    fun doNotCleanupUnsampledAreaForMaxAge() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarUnsampledAge = 1.hours
        unsampledHoldingOldestFileUpdatedMs =
            combinedTimeProvider.now().timestamp.toEpochMilli() - maxMarUnsampledAge.inWholeMilliseconds + 1

        runTest {
            holdingArea.addMarFile(marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest1)
            verify(exactly = 0) { unsampledHoldingArea.cleanup(any(), any()) }
        }
    }

    @Test
    fun noCleanup() {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        val marFile1Size = marFile1.length()

        unsampledHoldingAreaUsedBytes = 0
        maxMarStorageBytes = marFile1Size

        runTest {
            holdingArea.addMarFile(marFileWithManifest1)
            assertMarFileInHoldingArea(sampled = true, unsampled = false, marFile = marFileWithManifest1)
            verify(exactly = 0) { unsampledHoldingArea.cleanup(any(), any()) }
        }
    }

    companion object {
        private const val NEW_REVISION = 543

        fun deviceConfig(timeMs: Long) = MarManifest(
            collectionTime = MarFileWriterTest.time(timeMs),
            type = "android-device-config",
            device = MarFileWriterTest.device,
            metadata = MarMetadata.DeviceConfigMarMetadata(
                revision = NEW_REVISION,
            ),
            debuggingResolution = Resolution.OFF,
            loggingResolution = Resolution.OFF,
            monitoringResolution = Resolution.OFF,
        )
    }

    private fun createHoldingArea(
        batchMarUploads: Boolean,
        clientServerMode: ClientServerMode,
    ) = MarFileHoldingArea(
        sampledHoldingDirectory = sampledHoldingDirectory,
        batchMarUploads = { batchMarUploads },
        marFileWriter = marFileWriter,
        oneTimeMarUpload = oneTimeMarUpload,
        cachedClientServerMode = mockk { coEvery { get() } returns clientServerMode },
        currentSamplingConfig = currentSamplingConfig,
        linkedDeviceFileSender = linkedDeviceFileSender,
        unsampledHoldingArea = unsampledHoldingArea,
        combinedTimeProvider = combinedTimeProvider,
        maxMarStorageBytes = { maxMarStorageBytes },
        marMaxUnsampledAge = { maxMarUnsampledAge },
        marMaxUnsampledBytes = { maxMarUnsampledBytes },
        deviceInfoProvider = FakeDeviceInfoProvider(),
        projectKey = { "" },
    )

    private fun assertMarFileInHoldingArea(
        sampled: Boolean,
        unsampled: Boolean,
        marFile: MarFileWithManifest,
    ) {
        val sampledFile = File(sampledHoldingDirectory, marFile.marFile.name)
        assertEquals(sampled, sampledFile.exists())
        verify(exactly = if (unsampled) 1 else 0) { unsampledHoldingArea.add(marFile) }
    }
}
