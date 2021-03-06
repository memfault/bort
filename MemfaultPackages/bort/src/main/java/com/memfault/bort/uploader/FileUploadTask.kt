package com.memfault.bort.uploader

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.memfault.bort.BortJson
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.FileUploader
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.enqueueWorkOnce
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.Logger
import java.io.File
import kotlin.time.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BACKOFF_DURATION = 5.minutes

private const val PATH_KEY = "PATH"
private const val METADATA_KEY = "METADATA"
private const val CONTINUATION_KEY = "CONTINUATION"
private const val SHOULD_COMPRESS_KEY = "SHOULD_COMPRESS"

data class FileUploadTaskInput(
    val file: File,
    val payload: FileUploadPayload,
    val continuation: FileUploadContinuation? = null,
    val shouldCompress: Boolean = true,
) {
    fun toWorkerInputData(): Data =
        workDataOf(
            PATH_KEY to file.path,
            METADATA_KEY to BortJson.encodeToString(FileUploadPayload.serializer(), payload),
            CONTINUATION_KEY to continuation?.let { BortJson.encodeToString(FileUploadContinuation.serializer(), it) },
            SHOULD_COMPRESS_KEY to shouldCompress,
        )

    companion object {
        fun fromData(inputData: Data) =
            FileUploadTaskInput(
                file = File(checkNotNull(inputData.getString(PATH_KEY), { "File path missing" })),
                payload = BortJson.decodeFromString(
                    FileUploadPayload.serializer(),
                    checkNotNull(inputData.getString(METADATA_KEY)) { "Metadata missing" }
                ),
                continuation = inputData.getString(CONTINUATION_KEY)?.let {
                    BortJson.decodeFromString(FileUploadContinuation.serializer(), it)
                },
                shouldCompress = inputData.getBoolean(SHOULD_COMPRESS_KEY, false),
            )
    }
}

internal class FileUploadTask(
    private val delegate: FileUploader,
    private val bortEnabledProvider: BortEnabledProvider,
    private val getUploadCompressionEnabled: () -> Boolean,
    override val getMaxAttempts: () -> Int = { 3 },
) : Task<FileUploadTaskInput>() {
    suspend fun upload(file: File, payload: FileUploadPayload, shouldCompress: Boolean): TaskResult {
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

        when (
            val result = delegate.upload(
                file, payload, shouldCompress && getUploadCompressionEnabled()
            )
        ) {
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
            upload(input.file, input.payload, input.shouldCompress).also {
                Logger.logEvent("upload", "result", it.toString())
                "UploadWorker result=$it payloadClass=${input.payload.javaClass.simpleName}".also { message ->
                    Logger.v(message)
                    Logger.test(message)
                }
            }.also { result ->
                input.continuation?.let {
                    when (result) {
                        TaskResult.SUCCESS -> it.success(worker.applicationContext)
                        TaskResult.FAILURE -> it.failure(worker.applicationContext)
                        TaskResult.RETRY -> Unit
                    }
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
    getUploadConstraints: () -> Constraints,
    debugTag: String,
    continuation: FileUploadContinuation? = null,
    shouldCompress: Boolean = true,
): WorkRequest =
    enqueueWorkOnce<FileUploadTask>(
        context,
        FileUploadTaskInput(file, payload, continuation, shouldCompress).toWorkerInputData()
    ) {
        setConstraints(getUploadConstraints())
        setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DURATION.toJavaDuration())
        addTag(debugTag)
    }

typealias EnqueueFileUpload = (
    file: File,
    payload: FileUploadPayload,
    debugTag: String,
) -> Unit
