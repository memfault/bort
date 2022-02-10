package com.memfault.bort.uploader

import androidx.work.Data
import androidx.work.workDataOf
import com.memfault.bort.BortJson
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.FileUploader
import com.memfault.bort.Payload.LegacyPayload
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.UPLOAD_FILE_FILE_MISSING
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.MaxUploadAttempts
import com.memfault.bort.settings.UploadCompressionEnabled
import com.memfault.bort.shared.Logger
import java.io.File
import javax.inject.Inject
import kotlin.time.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val BACKOFF_DURATION = 5.minutes

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

class FileUploadTask @Inject constructor(
    private val delegate: FileUploader,
    private val bortEnabledProvider: BortEnabledProvider,
    private val getUploadCompressionEnabled: UploadCompressionEnabled,
    private val maxUploadAttempts: MaxUploadAttempts,
    override val metrics: BuiltinMetricsStore,
) : Task<FileUploadTaskInput>() {
    suspend fun upload(file: File, payload: FileUploadPayload, shouldCompress: Boolean): TaskResult {
        fun fail(message: String): TaskResult {
            Logger.w("upload.failed", mapOf("message" to message, "file" to file.path))
            return TaskResult.FAILURE
        }

        Logger.v("Uploading ${file.path}")
        if (!bortEnabledProvider.isEnabled()) {
            return fail("Bort not enabled")
        }

        if (!file.exists()) {
            metrics.increment(UPLOAD_FILE_FILE_MISSING)
            return fail("File does not exist")
        }

        when (
            val result = delegate.upload(
                file, LegacyPayload(payload), shouldCompress && getUploadCompressionEnabled()
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

    override val getMaxAttempts: () -> Int
        get() = maxUploadAttempts
}
