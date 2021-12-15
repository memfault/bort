package com.memfault.bort.clientserver

import android.content.Context
import com.memfault.bort.MarFileHoldingDir
import com.memfault.bort.clientserver.MarUploadTask.Companion.enqueueOneTimeMarUpload
import com.memfault.bort.settings.BatchMarUploads
import com.memfault.bort.settings.UploadConstraints
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * We keep all mar files waiting for upload in [holdingDirectory], then whenever [MarUploadTask] runs (either periodic,
 * or one-time if invoke [enqueueOneTimeMarUpload] below), it will request a batched mar from this class.
 */
@Singleton
class MarFileHoldingArea @Inject constructor(
    @MarFileHoldingDir private val holdingDirectory: File,
    private val batchMarUploads: BatchMarUploads,
    private val context: Context,
    private val constraints: UploadConstraints,
    private val marFileWriter: MarFileWriter,
) {
    private val mutex = Mutex()

    init {
        holdingDirectory.mkdirs()
    }

    // TODO consider max storage limit + max individual upload limit

    suspend fun addMarFile(file: File) = mutex.withLock {
        file.renameTo(File(holdingDirectory, file.name))
        // Periodic task will upload mar files, if we are batching.
        if (batchMarUploads()) return

        // Upload immediately, if not batching.
        // Note: we don't upload a specific file - we create a new task to upload all available files.
        enqueueOneTimeMarUpload(context, constraints())
    }

    suspend fun getMarForUpload(): File? = mutex.withLock {
        val pendingFiles = holdingDirectory.listFiles()?.asList() ?: emptyList()

        // Note: we always batch uploads at this point (even if the preference is disabled).
        return marFileWriter.batchMarFiles(pendingFiles, holdingDirectory)
    }
}
