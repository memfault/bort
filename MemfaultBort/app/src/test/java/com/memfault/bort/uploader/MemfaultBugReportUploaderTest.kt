package com.memfault.bort.uploader

import androidx.work.ListenableWorker
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

class MemfaultBugReportUploaderTest {
    @get:Rule
    val server = MockWebServer()

    lateinit var file: File

    @Before
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
            MemfaultBugReportUploader(
                preparedUploader = createUploader(server)
            ).upload(file)
        }
        assert(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun prepareRetriesOn500() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = runBlocking {
            MemfaultBugReportUploader(
                preparedUploader = createUploader(server)
            ).upload(file)
        }
        assert(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun prepareWithNoResponseBodyRetries() {
        server.enqueue(MockResponse())
        val result = runBlocking {
            MemfaultBugReportUploader(
                preparedUploader = createUploader(server)
            ).upload(file)
        }
        assert(result is ListenableWorker.Result.Retry)
    }
}
