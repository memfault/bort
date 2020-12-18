package com.memfault.bort.uploader

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.workDataOf
import com.memfault.bort.BortEnabledProvider
import com.memfault.bort.BortJson
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.FileUploader
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.enqueueWorkOnce
import com.memfault.bort.shared.Logger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PATH_KEY = "PATH"
private const val METADATA_KEY = "METADATA"

data class FileUploadTaskInput(
    val file: File,
    val payload: FileUploadPayload,
) {
    fun toWorkerInputData(): Data =
        workDataOf(
            PATH_KEY to file.path,
            METADATA_KEY to BortJson.encodeToString(FileUploadPayload.serializer(), payload)
        )

    companion object {
        fun fromData(inputData: Data) =
            FileUploadTaskInput(
                file = File(checkNotNull(inputData.getString(PATH_KEY), { "File path missing" })),
                payload = BortJson.decodeFromString(
                    FileUploadPayload.serializer(),
                    checkNotNull(inputData.getString(METADATA_KEY)) { "Metadata missing" }
                ),
            )
    }
}

internal class FileUploadTask(
    private val delegate: FileUploader,
    private val bortEnabledProvider: BortEnabledProvider,
    override val maxAttempts: Int = 3
) : Task<FileUploadTaskInput>() {
    suspend fun upload(file: File, payload: FileUploadPayload): TaskResult {
        fun fail(message: String): TaskResult {
            Logger.e("$message file=(${file.path})")
            return TaskResult.FAILURE
        }

        Logger.v("Uploading ${file.path}")
        if (!bortEnabledProvider.isEnabled()) {
            return fail("Bort not enabled")
        }

        if (!file.exists()) {
            return fail("File does not exist")
        }

        when (val result = delegate.upload(file, payload)) {
            TaskResult.RETRY -> return result
            TaskResult.FAILURE -> return fail("Upload failed")
        }

        file.delete()
        return TaskResult.SUCCESS
    }

    override fun finally(input: FileUploadTaskInput?) {
        input?.let {
            input.file.delete()
        }
    }

    override suspend fun doWork(worker: TaskRunnerWorker, input: FileUploadTaskInput): TaskResult =
        withContext(Dispatchers.IO) {
            Logger.logEvent("upload", "start", worker.runAttemptCount.toString())
            upload(input.file, input.payload).also {
                Logger.logEvent("upload", "result", it.toString())
                "UploadWorker result: $it".also { message ->
                    Logger.v(message)
                    Logger.test(message)
                }
            }
        }

    override fun convertAndValidateInputData(inputData: Data): FileUploadTaskInput =
        FileUploadTaskInput.fromData(inputData)
}

fun enqueueFileUploadTask(
    context: Context,
    file: File,
    payload: FileUploadPayload,
    uploadConstraints: Constraints,
    debugTag: String
) {
    enqueueWorkOnce<FileUploadTask>(
        context,
        FileUploadTaskInput(file, payload).toWorkerInputData()
    ) {
        setConstraints(uploadConstraints)
        addTag(debugTag)
    }
}

typealias EnqueueFileUpload = (
    file: File,
    payload: FileUploadPayload,
    debugTag: String,
) -> Unit
