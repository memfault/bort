package com.memfault.bort.uploader

import androidx.work.workDataOf
import com.memfault.bort.FileUploadMetadata
import com.memfault.bort.FileUploader
import com.memfault.bort.TaskResult
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BugReportUploaderTest {

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
    fun missingPathFails() {
        val worker = mockTaskRunnerWorker(workDataOf())
        val result = runBlocking {
            BugReportUploader(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun maxUploadAttemptFails() {
        val worker = mockTaskRunnerWorker(
            makeBugreportUploadInputData(filePath = file.toString()),
            runAttemptCount = 4
        )
        val result = runBlocking {
            BugReportUploader(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider(),
                maxAttempts = 3
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
        assertFalse(file.exists())
    }

    @Test
    fun badPathFails() {
        val worker = mockTaskRunnerWorker(makeBugreportUploadInputData(filePath = "abcd"))
        val result = runBlocking {
            BugReportUploader(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun fileDeletedOnSuccess() {
        val worker = mockTaskRunnerWorker(makeBugreportUploadInputData(filePath = file.toString()))
        val result = runBlocking {
            BugReportUploader(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.SUCCESS)
        assertFalse(file.exists())
    }

    @Test
    fun fileDeletedWhenBortDisabled() {
        val worker = mockTaskRunnerWorker(makeBugreportUploadInputData(filePath = file.toString()))
        val result = runBlocking {
            BugReportUploader(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider(enabled = false)
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
        assertFalse(file.exists())
    }
}

internal fun fakeFileUploader(result: TaskResult = TaskResult.SUCCESS): FileUploader =
    object : FileUploader {
        override suspend fun upload(
            file: File,
            metadata: FileUploadMetadata
        ): TaskResult = result
    }
