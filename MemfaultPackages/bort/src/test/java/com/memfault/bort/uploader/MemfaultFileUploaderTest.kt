package com.memfault.bort.uploader

import com.memfault.bort.FileUploadToken
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload
import com.memfault.bort.TaskResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class MemfaultFileUploaderTest {
    @get:Rule
    val server = MockWebServer()

    lateinit var file: File

    @BeforeEach
    fun loadFile() {
        file = File.createTempFile(UUID.randomUUID().toString(), "").apply {
            deleteOnExit()
        }.also {
            Files.copy(
                loadTestFileFromResources().toPath(),
                it.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun fileUploadPayload() = Payload.MarPayload(
        MarFileUploadPayload(
            file = FileUploadToken("", "", ""),
            hardwareVersion = "",
            deviceSerial = "",
            softwareVersion = "",
            softwareType = "",
        ),
    )

    @Test
    fun prepareFailsOnBadCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        val result = MemfaultFileUploader(
            preparedUploader = createUploader(server),
        ).upload(file, fileUploadPayload(), shouldCompress = true)
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun prepareRetriesOn500() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = MemfaultFileUploader(
            preparedUploader = createUploader(server),
        ).upload(file, fileUploadPayload(), shouldCompress = true)

        assert(result == TaskResult.RETRY)
    }

    @Test
    fun prepareWithNoResponseBodyRetries() = runTest {
        server.enqueue(MockResponse())
        val result = MemfaultFileUploader(
            preparedUploader = createUploader(server),
        ).upload(file, fileUploadPayload(), shouldCompress = true)

        assert(result == TaskResult.RETRY)
    }
}
