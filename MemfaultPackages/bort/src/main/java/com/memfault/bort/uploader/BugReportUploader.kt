package com.memfault.bort.uploader

import androidx.work.Data
import androidx.work.workDataOf
import com.memfault.bort.BortEnabledProvider
import com.memfault.bort.FileUploader
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.shared.Logger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BugReportUploader(
    private val delegate: FileUploader,
    private val bortEnabledProvider: BortEnabledProvider,
    override val maxAttempts: Int = 3
) : Task<String>() {
    suspend fun upload(filePath: String): TaskResult {
        fun fail(message: String): TaskResult {
            Logger.e("$message file=($filePath)")
            return TaskResult.FAILURE
        }

        Logger.v("uploading $filePath")
        if (!bortEnabledProvider.isEnabled()) {
            return fail("Bort not enabled")
        }

        val file = File(filePath)
        if (!file.exists()) {
            return fail("File does not exist")
        }

        when (val result = delegate.upload(file)) {
            TaskResult.RETRY -> return result
            TaskResult.FAILURE -> return fail("Upload failed")
        }

        file.delete()
        return TaskResult.SUCCESS
    }

    override fun finally(input: String?) {
        input?.let {
            File(it).delete()
        }
    }

    override suspend fun doWork(worker: TaskRunnerWorker, input: String): TaskResult = withContext(Dispatchers.IO) {
        Logger.logEvent("upload", "start", worker.runAttemptCount.toString())
        upload(input).also {
            Logger.logEvent("upload", "result", it.toString())
            "UploadWorker result: $it".also { message ->
                Logger.v(message)
                Logger.test(message)
            }
        }
    }

    override fun convertAndValidateInputData(inputData: Data): String =
        checkNotNull(inputData.getString(INTENT_EXTRA_BUGREPORT_PATH), { "bugreport path missing" })
}

fun makeBugreportUploadInputData(filePath: String): Data =
    workDataOf(INTENT_EXTRA_BUGREPORT_PATH to filePath)
