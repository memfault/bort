package com.memfault.bort.uploader

import androidx.work.workDataOf
import com.memfault.bort.FileUploadToken
import com.memfault.bort.FileUploader
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.Payload
import com.memfault.bort.TaskResult
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

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
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun fileUploadPayload() = Payload.MarPayload(
        MarFileUploadPayload(
            file = FileUploadToken(
                md5 = "",
                name = "",
            ),
            hardwareVersion = "",
            deviceSerial = "",
            softwareVersion = "",
            softwareType = "",
        ),
    )

    @Test
    fun missingPathFails() = runTest {
        val worker = mockTaskRunnerWorker(workDataOf())
        val result = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
        ).doWork(worker)
        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun maxUploadAttemptFails() = runTest {
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(file, fileUploadPayload(), shouldCompress = true).toWorkerInputData(),
            runAttemptCount = 4,
        )
        val result = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
        ).doWork(worker)

        assert(result == TaskResult.FAILURE)
        assertFalse(file.exists())
    }

    @Test
    fun badPathFails() = runTest {
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(
                File("abcd"),
                fileUploadPayload(),
                shouldCompress = true,
            ).toWorkerInputData(),
        )
        val result = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
        ).doWork(worker)

        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun failureToDeserializeMetadata() = runTest {
        val worker = mockTaskRunnerWorker(
            workDataOf(
                "PATH" to file.path,
                "METADATA" to "{}",
            ),
        )
        val result = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
        ).doWork(worker)

        assert(result == TaskResult.FAILURE)
    }

    @Test
    fun fileDeletedOnSuccess() = runTest {
        val mockUploader = spyk(fakeFileUploader())
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(file, fileUploadPayload(), shouldCompress = true).toWorkerInputData(),
        )
        val result = FileUploadTask(
            delegate = mockUploader,
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
        ).doWork(worker)

        assert(result == TaskResult.SUCCESS)
        assertFalse(file.exists())
        coVerify { mockUploader.upload(file, ofType(Payload.MarPayload::class), shouldCompress = true) }
    }

    @Test
    fun fileDeletedWhenBortDisabled() = runTest {
        val worker = mockTaskRunnerWorker(
            FileUploadTaskInput(file, fileUploadPayload(), shouldCompress = true).toWorkerInputData(),
        )
        val result = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(enabled = MutableStateFlow(false)),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
        ).doWork(worker)

        assert(result == TaskResult.FAILURE)
        assertFalse(file.exists())
    }
}

internal fun fakeFileUploader(result: TaskResult = TaskResult.SUCCESS): FileUploader =
    object : FileUploader {
        override suspend fun upload(
            file: File,
            payload: Payload,
            shouldCompress: Boolean,
        ): TaskResult = result
    }
