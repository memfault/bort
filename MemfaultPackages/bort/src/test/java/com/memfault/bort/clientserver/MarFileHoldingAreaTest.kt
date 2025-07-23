package com.memfault.bort.clientserver

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.first
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.memfault.bort.BortJson
import com.memfault.bort.DevModeDisabled
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.clientserver.MarFileHoldingArea.Companion.manifestFileForMar
import com.memfault.bort.clientserver.MarFileWriterTest.Companion.FILE_CONTENT
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.Resolution
import com.memfault.bort.settings.Resolution.HIGH
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.ClientServerMode.CLIENT
import com.memfault.bort.shared.ClientServerMode.DISABLED
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@RunWith(TestParameterInjector::class)
class MarFileHoldingAreaTest {
    private val combinedTimeProvider = FakeCombinedTimeProvider

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()
    private lateinit var sampledHoldingDirectory: File
    private lateinit var unsampledHoldingDirectory: File
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

    private var maxMarStorageBytes: Long = 999_999_999
    private var maxMarSampledAge: Duration = Duration.ZERO
    private var maxMarUnsampledAge: Duration = Duration.ZERO
    private var maxMarUnsampledBytes: Long = 999_999_999

    private var unbatchUploads: Boolean = false

    @Before
    fun setup() {
        sampledHoldingDirectory = tempFolder.newFolder("sampled")
        unsampledHoldingDirectory = tempFolder.newFolder("unsampled")
    }

    @Test
    fun sampled_immediate() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.addMarFile(marFileWithManifest)
        coVerify(exactly = 1) { oneTimeMarUpload.uploadMarFile(marFile) }
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun sampled_batched() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.addMarFile(marFileWithManifest)
        coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }

        val sampledFile = File(sampledHoldingDirectory, marFileWithManifest.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactly(sampledFile)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun sampled_clientServer() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = CLIENT)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.addMarFile(marFileWithManifest)
        coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        coVerify(exactly = 1) {
            linkedDeviceFileSender.sendFileToLinkedDevice(
                file = marFile,
                dropboxTag = CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG,
            )
        }
    }

    @Test
    fun unsampled_stored() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.addMarFile(marFileWithManifest)
        coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }

        val unsampledFile = File(unsampledHoldingDirectory, marFileWithManifest.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(
                unsampledFile,
                manifestFileForMar(unsampledHoldingDirectory, unsampledFile),
            )
    }

    @Test
    fun unsampled_clientServer() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = false, clientServerMode = CLIENT)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)
        holdingArea.addMarFile(marFileWithManifest)
        coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(marFile) }

        val unsampledFile = File(unsampledHoldingDirectory, marFileWithManifest.marFile.name)
        val unsampledManifest = manifestFileForMar(unsampledHoldingDirectory, unsampledFile)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(unsampledFile, unsampledManifest)
    }

    @Test
    fun moveFromUnsampled() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.logcat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        // Add file to the "unsampled" holding area's "eligible for upload" queue.
        holdingArea.addMarFile(marFileWithManifest)

        val unsampledFile = File(unsampledHoldingDirectory, marFileWithManifest.marFile.name)
        val unsampledManifest = manifestFileForMar(unsampledHoldingDirectory, unsampledFile)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(unsampledFile, unsampledManifest)

        holdingArea.handleSamplingConfigChange(SamplingConfig(revision = NEW_REVISION, loggingResolution = HIGH))

        val sampledFile = File(sampledHoldingDirectory, marFileWithManifest.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(
                File(sampledHoldingDirectory, deviceConfigMar.marFile.name),
                sampledFile,
            )
        // Additional mar file was created + added (to sampled) with handled revision.
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()

        verify(exactly = 1) { oneTimeMarUpload.batchAndUpload() }
    }

    @Test
    fun moveFromUnsampled_noEligibleFiles() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()

        holdingArea.handleSamplingConfigChange(SamplingConfig(revision = NEW_REVISION, loggingResolution = HIGH))

        // File was moved from unsampled -> sampled.
        val sampledFile = File(sampledHoldingDirectory, deviceConfigMarFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(sampledFile)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()

        verify(exactly = 0) { oneTimeMarUpload.batchAndUpload() }
    }

    @Test
    fun cleanupSampledArea() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        val manifest2 = MarFileWriterTest.logcat(timeMs = 222222222)
        val marFile2 = MarFileWriterTest.createMarFile("mar2.mar", manifest2, FILE_CONTENT)

        // Ensure that it has a later modification timestamp.
        marFile2.setLastModified(marFile1.lastModified() + 5)

        val marFileWithManifest2 = MarFileWithManifest(marFile = marFile2, manifest = manifest2)
        val marFile2Size = marFile2.length()

        holdingArea.addMarFile(marFileWithManifest1)

        val sampledFile1 = File(sampledHoldingDirectory, marFileWithManifest1.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().containsExactly(sampledFile1)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()

        maxMarStorageBytes = marFile2Size
        holdingArea.addMarFile(marFileWithManifest2)

        coVerify(exactly = 0) { oneTimeMarUpload.uploadMarFile(any()) }

        val sampledFile2 = File(sampledHoldingDirectory, marFileWithManifest2.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().containsExactly(sampledFile2)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().containsExactly()
    }

    @Test
    fun cleanupSampledAreaForMaxAge() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarSampledAge = 1.days
        combinedTimeProvider.now = combinedTimeProvider.now.copy(
            timestamp = Instant.now().plus(maxMarSampledAge.times(1.5).toJavaDuration()),
        )

        holdingArea.addMarFile(marFileWithManifest1)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    enum class MaxAgeTestCase(val expired: Boolean, val maxAge: Duration, val timeAfterFile: Duration) {
        BEFORE_EXPIRY(false, 2.days, 1.days),
        AFTER_EXPIRY(true, 2.days, 4.days),
    }

    @Test
    fun doNotCleanupSampledAreaForMaxAge(@TestParameter testCase: MaxAgeTestCase) = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarSampledAge = testCase.maxAge
        combinedTimeProvider.now = combinedTimeProvider.now.copy(
            timestamp = Instant.now().plus(testCase.timeAfterFile.toJavaDuration()),
        )

        holdingArea.addMarFile(marFileWithManifest1)

        if (testCase.expired) {
            assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        } else {
            val sampledFile = File(sampledHoldingDirectory, marFileWithManifest1.marFile.name)
            assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
                .containsExactly(sampledFile)
        }
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun cleanupUnsampledArea() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        val marFile1Size = marFile1.length()

        val offset = 5
        maxMarStorageBytes = marFile1Size + offset

        holdingArea.addMarFile(marFileWithManifest1)

        val sampledFile = File(sampledHoldingDirectory, marFileWithManifest1.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactly(sampledFile)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun cleanupUnsampledAreaForMaxAge() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarUnsampledAge = 1.hours

        holdingArea.addMarFile(marFileWithManifest1)

        val sampledFile = File(sampledHoldingDirectory, marFileWithManifest1.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(sampledFile)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun cleanupUnsampledAreaForMaxBytes() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarUnsampledBytes = 1

        holdingArea.addMarFile(marFileWithManifest1)

        val sampledFile = File(sampledHoldingDirectory, marFileWithManifest1.marFile.name)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(sampledFile)
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun doNotCleanupUnsampledAreaForMaxAge(@TestParameter testCase: MaxAgeTestCase) = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)

        maxMarUnsampledAge = testCase.maxAge
        combinedTimeProvider.now = combinedTimeProvider.now.copy(
            timestamp = Instant.now().plus(testCase.timeAfterFile.toJavaDuration()),
        )

        holdingArea.addMarFile(marFileWithManifest1)

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()

        if (testCase.expired) {
            assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        } else {
            val unsampledFile = File(unsampledHoldingDirectory, marFileWithManifest1.marFile.name)
            val unsampledManifest = manifestFileForMar(unsampledHoldingDirectory, unsampledFile)
            assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
                .containsExactlyInAnyOrder(unsampledFile, unsampledManifest)
        }
    }

    @Test
    fun noCleanup() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.logcat(timeMs = 123456789)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        val marFile1Size = marFile1.length()

        maxMarStorageBytes = marFile1Size

        holdingArea.addMarFile(marFileWithManifest1)

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactly(File(sampledHoldingDirectory, marFileWithManifest1.marFile.name))
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun unsampledAddAndRemove() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        assertThat(holdingArea.unsampledEligibleForUpload(SamplingConfig())).isEmpty()

        holdingArea.addMarFile(marFileWithManifest)

        val unsampledFile = File(unsampledHoldingDirectory, marFileWithManifest.marFile.name)
        val unsampledManifest = manifestFileForMar(unsampledHoldingDirectory, unsampledFile)
        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(
                unsampledFile,
                unsampledManifest,
            )

        // Not returned when config isn't high enough.
        assertThat(holdingArea.unsampledEligibleForUpload(SamplingConfig())).isEmpty()

        // Run cleanup (no-op)
        holdingArea.cleanupIfRequired()

        val eligible = holdingArea.unsampledEligibleForUpload(SamplingConfig(monitoringResolution = HIGH))
        assertThat(eligible).first().transform { it.manifest }.isEqualTo(manifest)

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(
                unsampledFile,
                unsampledManifest,
            )
    }

    @Test
    fun unsampledCleanup_deletesOrphanManifest() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest1 = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile1 = MarFileWriterTest.createMarFile("mar1.mar", manifest1, FILE_CONTENT)
        val marFileWithManifest1 = MarFileWithManifest(marFile = marFile1, manifest = manifest1)
        holdingArea.addMarFile(marFileWithManifest1)

        val manifest2 = MarFileWriterTest.heartbeat(timeMs = 223456789, resolution = HIGH)
        val marFile2 = MarFileWriterTest.createMarFile("mar2.mar", manifest2, FILE_CONTENT)
        val marFileWithManifest2 = MarFileWithManifest(marFile = marFile2, manifest = manifest2)
        val marFile2Size = marFile2.length()

        holdingArea.addMarFile(marFileWithManifest2)

        val sampledFile1 = File(unsampledHoldingDirectory, marFile1.name)
        val sampledManifest1 = manifestFileForMar(unsampledHoldingDirectory, marFile1)
        val unsampledFile2 = File(unsampledHoldingDirectory, marFile2.name)
        val unsampledManifest2 = manifestFileForMar(unsampledHoldingDirectory, marFile2)

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(
                sampledFile1,
                sampledManifest1,
                unsampledFile2,
                unsampledManifest2,
            )

        // Run cleanup - will delete the mar file (over limit), and should also cleanup the manifest file.
        val manifest1Size = BortJson.encodeToString(MarManifest.serializer(), manifest1).length
        val manifest2Size = BortJson.encodeToString(MarManifest.serializer(), manifest2).length

        // Ensure that it has a later modification timestamp.
        unsampledFile2.setLastModified(sampledFile1.lastModified() + 5)
        unsampledManifest2.setLastModified(sampledFile1.lastModified() + 5)

        maxMarStorageBytes = marFile2Size + manifest1Size + manifest2Size
        holdingArea.cleanupIfRequired()

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull()
            .containsExactlyInAnyOrder(
                unsampledFile2,
                unsampledManifest2,
            )

        val eligible = holdingArea.unsampledEligibleForUpload(SamplingConfig(monitoringResolution = HIGH))
        assertThat(eligible).first().transform { it.manifest }.isEqualTo(manifest2)
    }

    @Test
    fun unsampledCleanup_deletesOrphanMarFile() = runTest {
        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.heartbeat(timeMs = 123456789, resolution = HIGH)
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.addMarFile(marFileWithManifest)

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().hasSize(2)

        // Run cleanup (no-op)
        holdingArea.cleanupIfRequired()

        // Delete the mar file from the holding area
        val manifestFileInHoldingArea = File(unsampledHoldingDirectory, marFileWithManifest.marFile.name)
        manifestFileInHoldingArea.delete()

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().hasSize(1)
        assertThat(holdingArea.unsampledEligibleForUpload(SamplingConfig())).isEmpty()

        // Run cleanup
        holdingArea.cleanupIfRequired()

        assertThat(sampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
        assertThat(unsampledHoldingDirectory.listFiles()?.toList()).isNotNull().isEmpty()
    }

    @Test
    fun bugReportsUploadImmediately() = runTest {
        unbatchUploads = true

        val holdingArea = createHoldingArea(batchMarUploads = true, clientServerMode = DISABLED)
        val manifest = MarFileWriterTest.bugreport(timeMs = 123456789, requestId = "requested")
        val marFile = MarFileWriterTest.createMarFile("mar1.mar", manifest, FILE_CONTENT)
        val marFileWithManifest = MarFileWithManifest(marFile = marFile, manifest = manifest)

        holdingArea.addMarFile(marFileWithManifest)

        coVerify(exactly = 1) { oneTimeMarUpload.uploadMarFile(marFile) }
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
        unsampledHoldingDirectory = unsampledHoldingDirectory,
        batchMarUploads = { batchMarUploads },
        marFileWriter = marFileWriter,
        oneTimeMarUpload = oneTimeMarUpload,
        cachedClientServerMode = mockk { coEvery { get() } returns clientServerMode },
        currentSamplingConfig = currentSamplingConfig,
        linkedDeviceFileSender = linkedDeviceFileSender,
        combinedTimeProvider = combinedTimeProvider,
        maxMarStorageBytes = { maxMarStorageBytes },
        marMaxUnsampledAge = { maxMarUnsampledAge },
        marMaxUnsampledBytes = { maxMarUnsampledBytes },
        marMaxSampledAge = { maxMarSampledAge },
        deviceInfoProvider = FakeDeviceInfoProvider(),
        projectKey = { "" },
        devMode = DevModeDisabled,
        unbatchBugReportUploads = { unbatchUploads },
    )
}
