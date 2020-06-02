package com.memfault.bort.uploader

import androidx.work.ListenableWorker
import com.memfault.bort.FileUploader
import com.memfault.bort.Logger
import java.io.File

internal class DelegatingUploader(
    private val delegate: FileUploader,
    private val filePath: String?,
    private val maxUploadAttempts: Int = 3
) {

    suspend fun upload(
        runAttemptCount: Int
    ): ListenableWorker.Result {
        Logger.v("uploading $filePath")
        val path = filePath ?: return deleteAndFail("File path is null")

        if (runAttemptCount >= maxUploadAttempts) {
            return deleteAndFail("Reached max attempts $runAttemptCount of $maxUploadAttempts")
        }

        val file = File(path)
        if (!file.exists()) {
            return deleteAndFail("File does not exist")
        }

        when (val result = delegate.upload(file)) {
            is ListenableWorker.Result.Retry -> return result
            is ListenableWorker.Result.Failure -> return deleteAndFail("Upload failed")
        }

        file.delete()
        return ListenableWorker.Result.success()
    }

    private fun deleteAndFail(message: String): ListenableWorker.Result {
        Logger.e("$message file=($filePath)")
        filePath?.let {
            File(it).delete()
        }
        return ListenableWorker.Result.failure()
    }

}
