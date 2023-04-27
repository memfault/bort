package com.memfault.bort.uploader

import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.ProcessingOptions
import com.memfault.bort.clientserver.MarDevice
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.clientserver.MarFileWithManifest
import com.memfault.bort.clientserver.MarFileWriter
import com.memfault.bort.clientserver.MarManifest
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.settings.Resolution.NORMAL
import com.memfault.bort.settings.Resolution.NOT_APPLICABLE
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EnqueueUploadTest {
    private val marFile = File("fakepath")
    private val testFile = File("fakemarpath")
    private val PROJECT_KEY = "projectKey"
    private val marDevice = MarDevice(
        projectKey = PROJECT_KEY,
        hardwareVersion = "hardwareVersion",
        softwareVersion = "softwareVersion",
        softwareType = "softwareType",
        deviceSerial = "deviceSerial",
    )
    private val metadata = MarMetadata.BugReportMarMetadata(
        bugReportFileName = "filename",
        processingOptions = ProcessingOptions(),
    )
    private val marManifest = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = metadata,
        debuggingResolution = NORMAL,
        loggingResolution = NORMAL,
        monitoringResolution = NORMAL,
    )
    private val marAndManifest = MarFileWithManifest(marFile, marManifest)
    private val marFileWriter = mockk<MarFileWriter> {
        coEvery { createMarFile(any(), any()) } answers { Result.success(marAndManifest) }
    }
    private val marHoldingArea: MarFileHoldingArea = mockk(relaxed = true)
    private val enqueueUpload = EnqueueUpload(
        marFileWriter = marFileWriter,
        marHoldingArea = marHoldingArea,
        deviceInfoProvider = FakeDeviceInfoProvider(),
        projectKey = { PROJECT_KEY },
    )
    private val combinedTimeProvider = FakeCombinedTimeProvider

    @Test
    fun enqueueMarFileUpload() {
        val now = combinedTimeProvider.now()
        enqueueUpload.enqueue(
            file = testFile,
            metadata = metadata,
            collectionTime = now,
        )
        val slot = slot<MarManifest>()
        coVerify(exactly = 1, timeout = TIMEOUT_MS) {
            marFileWriter.createMarFile(testFile, capture(slot))
            marHoldingArea.addMarFile(marAndManifest)
        }
        assertEquals(NORMAL, slot.captured.debuggingResolution)
    }

    @Test
    fun overrideDebuggingResolution() {
        val now = combinedTimeProvider.now()
        enqueueUpload.enqueue(
            file = testFile,
            metadata = metadata,
            collectionTime = now,
            overrideDebuggingResolution = NOT_APPLICABLE,
        )
        val slot = slot<MarManifest>()
        coVerify(exactly = 1, timeout = TIMEOUT_MS) {
            marFileWriter.createMarFile(testFile, capture(slot))
            marHoldingArea.addMarFile(marAndManifest)
        }
        assertEquals(NOT_APPLICABLE, slot.captured.debuggingResolution)
    }

    companion object {
        private const val TIMEOUT_MS: Long = 1000
    }
}
