package com.memfault.bort.uploader

import android.content.Context
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.clientserver.MarDevice
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.clientserver.MarFileWithManifest
import com.memfault.bort.clientserver.MarFileWriter
import com.memfault.bort.clientserver.MarManifest
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.Resolution
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.shared.ClientServerMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import retrofit2.Call

internal class EnqueueUploadTest {
    private var clientServerMode: ClientServerMode = ClientServerMode.DISABLED
    private val marFile = File("fakepath")
    private val testFile = File("fakemarpath")
    private val marDevice = MarDevice(
        projectKey = "projectKey",
        hardwareVersion = "hardwareVersion",
        softwareVersion = "softwareVersion",
        softwareType = "softwareType",
        deviceSerial = "deviceSerial",
    )
    private val marManifest = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = MarMetadata.BugReportMarMetadata(
            bugReportFileName = "filename",
            processingOptions = BugReportFileUploadPayload.ProcessingOptions(),
        ),
        debuggingResolution = Resolution.NORMAL,
        loggingResolution = Resolution.NORMAL,
        monitoringResolution = Resolution.NORMAL,
    )
    private val marAndManifest = MarFileWithManifest(marFile, marManifest)
    private var shouldUpload: Boolean = true
    private val marFileWriter = mockk<MarFileWriter> {
        coEvery { createForReboot(any(), any()) } answers { marAndManifest }
        coEvery { createForFile(any(), any(), any()) } answers { marAndManifest }
        coEvery { checkShouldUpload(any(), any(), any(), any()) } answers { shouldUpload }
    }
    private val ingressServiceCall = mockk<Call<Unit>>(relaxed = true)
    private val ingressService = mockk<IngressService> {
        every { uploadRebootEvents(any()) } returns ingressServiceCall
    }
    private val context = mockk<Context>()
    private val enqueuePreparedUploadTask = mockk<EnqueuePreparedUploadTask>(relaxed = true)
    private val marHoldingArea: MarFileHoldingArea = mockk(relaxed = true)
    private val currentSamplingConfig = mockk<CurrentSamplingConfig> { coEvery { get() } returns SamplingConfig() }
    private val enqueueUpload = EnqueueUpload(
        context = context,
        marFileWriter = marFileWriter,
        ingressService = ingressService,
        enqueuePreparedUploadTask = enqueuePreparedUploadTask,
        useMarUpload = { useMarUpload },
        marHoldingArea = marHoldingArea,
        currentSamplingConfig = currentSamplingConfig,
    )
    private val continuation = mockk<FileUploadContinuation>(relaxed = true)
    private val combinedTimeProvider = FakeCombinedTimeProvider
    private val metadata = BugReportFileUploadPayload(
        hardwareVersion = "",
        deviceSerial = "",
        softwareVersion = "",
        softwareType = "",
    )
    private var useMarUpload = false

    @Test
    fun enqueueMarFileUpload() {
        clientServerMode = ClientServerMode.DISABLED
        useMarUpload = true
        enqueueUpload()
        verifySent(mar = true, preparedUpload = false)
        verify { continuation.success(context) }
    }

    @Test
    fun enqueuePreparedUpload() {
        clientServerMode = ClientServerMode.DISABLED
        useMarUpload = false
        enqueueUpload()
        verifySent(mar = false, preparedUpload = true)
    }

    @Test
    fun enqueuePreparedUpload_unsampled() {
        shouldUpload = false
        clientServerMode = ClientServerMode.DISABLED
        useMarUpload = false
        enqueueUpload()
        verifySent(mar = false, preparedUpload = false)
        assertFalse(testFile.exists())
    }

    private fun enqueueUpload() {
        enqueueUpload.enqueue(
            file = testFile,
            metadata = metadata,
            debugTag = "hello",
            collectionTime = combinedTimeProvider.now(),
            continuation = continuation,
        )
    }

    private fun Boolean.times() = if (this) 1 else 0

    private fun verifySent(mar: Boolean, preparedUpload: Boolean) {
        coVerify(exactly = preparedUpload.times(), timeout = TIMEOUT_MS) {
            enqueuePreparedUploadTask.upload(testFile, any(), any(), any(), any(), any())
        }
        coVerify(exactly = mar.times(), timeout = TIMEOUT_MS) {
            marHoldingArea.addMarFile(marAndManifest)
        }
    }

    companion object {
        private const val TIMEOUT_MS: Long = 1000
    }
}
