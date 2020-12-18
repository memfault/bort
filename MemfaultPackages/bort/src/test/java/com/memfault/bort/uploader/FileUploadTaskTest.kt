package com.memfault.bort.uploader

import androidx.work.workDataOf
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.FileUploader
import com.memfault.bort.TaskResult
import io.mockk.coVerify
import io.mockk.spyk
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileUploadTaskTest {

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
            FileUploadTask(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun maxUploadAttemptFails() {
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(file, BugReportFileUploadPayload()).toWorkerInputData(),
            runAttemptCount = 4
        )
        val result = runBlocking {
            FileUploadTask(
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
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(File("abcd"), BugReportFileUploadPayload()).toWorkerInputData()
        )
        val result = runBlocking {
            FileUploadTask(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun failureToDeserializeMetadata() {
        val worker = mockTaskRunnerWorker(
            workDataOf(
                "PATH" to file.path,
                "METADATA" to "{}",
            )
        )
        val result = runBlocking {
            FileUploadTask(
                delegate = fakeFileUploader(),
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun fileDeletedOnSuccess() {
        val mockUploader = spyk(fakeFileUploader())
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(file, BugReportFileUploadPayload()).toWorkerInputData()
        )
        val result = runBlocking {
            FileUploadTask(
                delegate = mockUploader,
                bortEnabledProvider = BortEnabledTestProvider()
            ).doWork(worker)
        }
        assert(result == TaskResult.SUCCESS)
        assertFalse(file.exists())
        coVerify { mockUploader.upload(file, ofType(BugReportFileUploadPayload::class)) }
    }

    @Test
    fun fileDeletedWhenBortDisabled() {
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(file, BugReportFileUploadPayload()).toWorkerInputData()
        )
        val result = runBlocking {
            FileUploadTask(
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
            payload: FileUploadPayload
        ): TaskResult = result
    }
