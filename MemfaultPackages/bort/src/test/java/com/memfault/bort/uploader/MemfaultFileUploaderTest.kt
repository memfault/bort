package com.memfault.bort.uploader

import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.TaskResult
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    @Test
    fun prepareFailsOnBadCode() {
        server.enqueue(MockResponse().setResponseCode(400))
        val result = runBlocking {
            MemfaultFileUploader(
                preparedUploader = createUploader(server)
            ).upload(file, BugReportFileUploadPayload())
        }
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun prepareRetriesOn500() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = runBlocking {
            MemfaultFileUploader(
                preparedUploader = createUploader(server)
            ).upload(file, BugReportFileUploadPayload())
        }
        assert(result == TaskResult.RETRY)
    }

    @Test
    fun prepareWithNoResponseBodyRetries() {
        server.enqueue(MockResponse())
        val result = runBlocking {
            MemfaultFileUploader(
                preparedUploader = createUploader(server)
            ).upload(file, BugReportFileUploadPayload())
        }
        assert(result == TaskResult.RETRY)
    }
}
