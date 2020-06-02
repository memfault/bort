package com.memfault.bort.uploader

import androidx.work.ListenableWorker
import com.memfault.bort.FileUploader
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

class DelegatingUploaderTest {

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
    fun nullPathFails() {
        val result = runBlocking {
            DelegatingUploader(
                delegate = fakeFileUploader(),
                filePath = null
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun maxUploadAttemptFails() {
        val result = runBlocking {
            DelegatingUploader(
                delegate = fakeFileUploader(),
                filePath = file.toString(),
                maxUploadAttempts = 3
            ).upload(3)
        }
        assert(result is ListenableWorker.Result.Failure)
        Assert.assertFalse(file.exists())
    }

    @Test
    fun badPathFails() {
        val result = runBlocking {
            DelegatingUploader(
                delegate = fakeFileUploader(),
                filePath = "abcd"
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun fileDeletedOnSuccess() {
        val result = runBlocking {
            DelegatingUploader(
                delegate = fakeFileUploader(),
                filePath = file.toString()
            ).upload(0)
        }
        assert(result is ListenableWorker.Result.Success)
        Assert.assertFalse(file.exists())
    }
}

internal fun fakeFileUploader(result: ListenableWorker.Result = ListenableWorker.Result.success()): FileUploader =
    object : FileUploader {
        override suspend fun upload(file: File): ListenableWorker.Result = result
    }
