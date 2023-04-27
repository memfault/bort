package com.memfault.bort.uploader

import androidx.work.Data
import androidx.work.workDataOf
import com.memfault.bort.BortJson
import com.memfault.bort.FileUploader
import com.memfault.bort.Payload
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
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val BACKOFF_DURATION = 5.minutes

private const val PATH_KEY = "PATH"
private const val METADATA_KEY = "METADATA"
private const val SHOULD_COMPRESS_KEY = "SHOULD_COMPRESS"

data class FileUploadTaskInput(
    val file: File,
    val payload: Payload,
    val shouldCompress: Boolean = true,
) {
    fun toWorkerInputData(): Data =
        workDataOf(
            PATH_KEY to file.path,
            METADATA_KEY to BortJson.encodeToString(Payload.serializer(), payload),
            SHOULD_COMPRESS_KEY to shouldCompress,
        )

    companion object {
        private fun deserializePayload(payload: String): Payload {
            return BortJson.decodeFromString(Payload.serializer(), payload)
        }

        fun fromData(inputData: Data) =
            FileUploadTaskInput(
                file = File(checkNotNull(inputData.getString(PATH_KEY), { "File path missing" })),
                payload = deserializePayload(checkNotNull(inputData.getString(METADATA_KEY)) { "Metadata missing" }),
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
    suspend fun upload(file: File, payload: Payload, shouldCompress: Boolean): TaskResult {
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
                file, payload, shouldCompress && getUploadCompressionEnabled()
            )
        ) {
            TaskResult.RETRY -> return result
            TaskResult.FAILURE -> return fail("Upload failed")
            else -> {}
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
                "UploadWorker result=$it payloadClass=${input.payload.payloadClassName()}".also { message ->
                    Logger.v(message)
                    Logger.test(message)
                }
            }
        }

    override fun convertAndValidateInputData(inputData: Data): FileUploadTaskInput =
        FileUploadTaskInput.fromData(inputData)

    override val getMaxAttempts: () -> Int
        get() = maxUploadAttempts

    companion object {
        fun Payload.payloadClassName() = when (this) {
            is Payload.MarPayload -> this.payload.javaClass.simpleName
        }
    }
}
