package com.memfault.bort.uploader

import androidx.work.ListenableWorker
import com.memfault.bort.SettingsProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

class BugReportUploaderTest {
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
    fun maxUploadAttemptFails() {
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = file.toString(),
                maxUploadAttempts = 3
            ).upload(3)
        }
        assert(result is ListenableWorker.Result.Failure)
        assertFalse(file.exists())
    }

    @Test
    fun nullPathFails() {
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = null
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun badPathFails() {
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = "abcd"
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun prepareFailsOnBadCode() {
        server.enqueue(MockResponse().setResponseCode(400))
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = file.toString()
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Failure)
        assertFalse(file.exists())
    }

    @Test
    fun prepareRetriesOn500() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = file.toString()
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Retry)
        assertTrue(file.exists())
    }

    @Test
    fun prepareWithNoResponseBodyRetries() {
        server.enqueue(MockResponse())
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = file.toString()
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Retry)
        assertTrue(file.exists())
    }

    @Test
    fun fileDeletedOnSuccess() {
        server.apply {
            enqueue(MockResponse().setBody(UPLOAD_RESPONSE))
            enqueue(MockResponse())
            enqueue(MockResponse())
        }
        val result = runBlocking {
            BugReportUploader(
                preparedUploader = createUploader(server),
                filePath = file.toString()
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Success)
        assertFalse(file.exists())
    }
}
