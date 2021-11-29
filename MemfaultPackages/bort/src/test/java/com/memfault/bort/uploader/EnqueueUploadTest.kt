package com.memfault.bort.uploader

import android.content.Context
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.DumpsterClient
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.clientserver.MarFileWriter
import com.memfault.bort.clientserver.ServerFileSender
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.shared.ClientServerMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Test
import retrofit2.Call

internal class EnqueueUploadTest {
    private var clientServerMode: String? = null
    private val dumpsterClient = mockk<DumpsterClient> {
        coEvery { getprop() } answers {
            clientServerMode?.let { mapOf(ClientServerMode.SYSTEM_PROP to it) } ?: emptyMap()
        }
    }
    private val serverFileSender = mockk<ServerFileSender>(relaxed = true)
    private val marFile = File("fakepath")
    private val testFile = File("fakemarpath")
    private val marFileWriter = mockk<MarFileWriter> {
        coEvery { createForReboot(any(), any()) } answers { marFile }
        coEvery { createForFile(any(), any(), any()) } answers { marFile }
    }
    private val ingressServiceCall = mockk<Call<Unit>>(relaxed = true)
    private val ingressService = mockk<IngressService> {
        every { uploadRebootEvents(any()) } returns ingressServiceCall
    }
    private val context = mockk<Context>()
    private val enqueuePreparedUploadTask = mockk<EnqueuePreparedUploadTask>(relaxed = true)
    private val enqueueUpload = EnqueueUpload(
        context = context,
        serverFileSender = serverFileSender,
        marFileWriter = marFileWriter,
        ingressService = ingressService,
        dumpsterClient = dumpsterClient,
        enqueuePreparedUploadTask = enqueuePreparedUploadTask,
    )
    private val continuation = mockk<FileUploadContinuation>(relaxed = true)
    private val combinedTimeProvider = FakeCombinedTimeProvider
    private val metadata = BugReportFileUploadPayload()

    @Test
    fun enqueueMarFileUpload() {
        clientServerMode = "client"
        enqueueUpload.enqueue(
            file = testFile,
            metadata = metadata,
            debugTag = "hello",
            collectionTime = combinedTimeProvider.now(),
            continuation = continuation,
        )

        verifyClientServerSent()
        verify { continuation.success(context) }
    }

    @Test
    fun marFileNotForwardedAgain() {
        val marMetadata = MarFileUploadPayload(
            file = FileUploadToken(token = "", md5 = "", name = ""),
            hardwareVersion = "",
            deviceSerial = "",
            softwareVersion = "",
            softwareType = "",
        )
        clientServerMode = "client"
        enqueueUpload.enqueue(
            file = testFile,
            metadata = marMetadata,
            debugTag = "hello",
            collectionTime = combinedTimeProvider.now(),
            continuation = continuation,
        )

        verifyPreparedUpload()
    }

    @Test
    fun enqueuePreparedUpload() {
        clientServerMode = null
        enqueueUpload.enqueue(
            file = testFile,
            metadata = metadata,
            debugTag = "hello",
            collectionTime = combinedTimeProvider.now(),
            continuation = continuation,
        )

        verifyPreparedUpload()
    }

    private fun verifyClientServerSent() {
        coVerify(exactly = 1) { serverFileSender.sendFileToBortServer(marFile) }
        coVerify(exactly = 0) { enqueuePreparedUploadTask.upload(any(), any(), any(), any(), any()) }
    }

    private fun verifyPreparedUpload() {
        coVerify(exactly = 0) { serverFileSender.sendFileToBortServer(any()) }
        coVerify(exactly = 1) { enqueuePreparedUploadTask.upload(testFile, any(), any(), any(), any()) }
    }
}
