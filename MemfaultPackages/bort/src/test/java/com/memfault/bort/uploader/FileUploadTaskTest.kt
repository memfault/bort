package com.memfault.bort.uploader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.workDataOf
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class FileUploadTaskTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var file: File

    @Before
    fun loadFile() {
        file = folder.newFile(UUID.randomUUID().toString())

        Files.copy(
            loadTestFileFromResources().toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun fileUploadPayload() = Payload.MarPayload(
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
        val task = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
            ioCoroutineContext = coroutineContext,
        )
        val worker = mockTaskRunnerWorker<FileUploadTask>(context, mockWorkerFactory(fileUpload = task), workDataOf())
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
    }

    @Test
    fun maxUploadAttemptFails() = runTest {
        val task = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
            ioCoroutineContext = coroutineContext,
        )

        val worker = mockTaskRunnerWorker<FileUploadTask>(
            context,
            mockWorkerFactory(fileUpload = task),
            FileUploadTaskInput(
                file,
                fileUploadPayload(),
                shouldCompress = true,
            ).toWorkerInputData(),
            runAttemptCount = 4,
        )

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun badPathFails() = runTest {
        val task = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
            ioCoroutineContext = coroutineContext,
        )
        val worker = mockTaskRunnerWorker<FileUploadTask>(
            context,
            mockWorkerFactory(fileUpload = task),
            FileUploadTaskInput(
                File("abcd"),
                fileUploadPayload(),
                shouldCompress = true,
            ).toWorkerInputData(),
        )
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
    }

    @Test
    fun failureToDeserializeMetadata() = runTest {
        val task = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
            ioCoroutineContext = coroutineContext,
        )
        val worker = mockTaskRunnerWorker<FileUploadTask>(
            context,
            mockWorkerFactory(fileUpload = task),
            workDataOf(
                "PATH" to file.path,
                "METADATA" to "{}",
            ),
        )
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
    }

    @Test
    fun fileDeletedOnSuccess() = runTest {
        val mockUploader = spyk(fakeFileUploader())
        val task = FileUploadTask(
            delegate = mockUploader,
            bortEnabledProvider = BortEnabledTestProvider(),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
            ioCoroutineContext = coroutineContext,
        )
        val worker = mockTaskRunnerWorker<FileUploadTask>(
            context,
            mockWorkerFactory(fileUpload = task),
            FileUploadTaskInput(
                file,
                fileUploadPayload(),
                shouldCompress = true,
            ).toWorkerInputData(),
        )

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(file.exists()).isFalse()
        coVerify {
            mockUploader.upload(
                file,
                ofType(Payload.MarPayload::class),
                shouldCompress = true,
            )
        }
    }

    @Test
    fun fileDeletedWhenBortDisabled() = runTest {
        val task = FileUploadTask(
            delegate = fakeFileUploader(),
            bortEnabledProvider = BortEnabledTestProvider(enabled = MutableStateFlow(false)),
            getUploadCompressionEnabled = { true },
            maxUploadAttempts = { 1 },
            metrics = mockk(relaxed = true),
            ioCoroutineContext = coroutineContext,
        )
        val worker = mockTaskRunnerWorker<FileUploadTask>(
            context,
            mockWorkerFactory(fileUpload = task),
            FileUploadTaskInput(
                file,
                fileUploadPayload(),
                shouldCompress = true,
            ).toWorkerInputData(),
        )
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(file.exists()).isFalse()
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
