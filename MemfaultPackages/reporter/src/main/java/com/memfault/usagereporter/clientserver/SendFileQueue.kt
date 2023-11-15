package com.memfault.usagereporter.clientserver

import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.ReporterSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Exposes the next file to be uploaded, via [nextFile].
 *
 * Call [pushOldestFile] after any file addition or deletion.
 */
interface SendFileQueue {
    fun pushOldestFile()
    fun createFile(dropboxTag: String): File
    fun incrementSendCount(file: File)
    val nextFile: StateFlow<File?>
}

class RealSendfileQueue(
    private val uploadDirectory: File,
    private val reporterSettings: ReporterSettings,
    private val maxRetryCount: Int = 20,
) : SendFileQueue {
    private val _nextFile = MutableStateFlow<File?>(null)
    override val nextFile = _nextFile.asStateFlow()

    // Note: tried using a FileObserver for this (to notify us when the filesystem changes) but it did not work
    // reliably.
    override fun pushOldestFile() {
        cleanup(uploadDirectory, reporterSettings)
        val oldestFile = uploadDirectory.listFiles()?.minByOrNull { it.lastModified() }
        _nextFile.value = oldestFile
    }

    override fun createFile(dropboxTag: String): File =
        File(uploadDirectory, "${UUID.randomUUID()}.$dropboxTag$INITIAL_COUNT_POSTFIX").apply {
            uploadDirectory.mkdirs()
            deleteSilently()
        }

    /**
     * Renames the file to increment the send attempt count. If over the max allowed, then delete the file.
     *
     * Format (see above): "<uuid>.<tag>.<sendcount>".
     * In a previous version this was "<uuid>.<tag>": we still support reading this.
     */
    override fun incrementSendCount(file: File) {
        val sections = file.name.split(".")
        val previousCount = sections.last().toIntOrNull()
        val newCount = (previousCount ?: 0) + 1
        if (newCount > maxRetryCount) {
            Logger.d("Deleting queued client/server file for retry limit: ${file.name}")
            file.deleteSilently()
            return
        }
        val nameWithoutCount = file.name.removeSuffix(".$previousCount")
        val newName = "$nameWithoutCount.$newCount"
        val newFile = File(file.parent, newName)
        file.renameTo(newFile)
    }

    companion object {
        private const val INITIAL_COUNT_POSTFIX = ".0"
        private val queuedDeletedMetric = Reporting.report().counter(
            name = "sendfile_queue_deleted",
            internal = true,
        )

        /**
         * Extract the dropbox tag from the filename. See above for pattern - this is either the last or penulatimate
         * (if the last is an integer) '.'-separated section.
         */
        fun File.extractDropboxTag(): String {
            val sections = name.split(".")
            return sections.last { it.toIntOrNull() == null }
        }

        // This is also called from [ReporterFileCleanupTask].
        fun cleanup(directory: File, reporterSettings: ReporterSettings) {
            val result =
                cleanupFiles(
                    dir = directory,
                    maxDirStorageBytes = reporterSettings.maxFileTransferStorageBytes,
                    maxFileAge = reporterSettings.maxFileTransferStorageAge,
                )
            val deleted = result.deletedForStorageCount + result.deletedForAgeCount
            if (deleted > 0) {
                Logger.d("Deleted $deleted files queued to send to remote device, to stay under storage limit.")
                queuedDeletedMetric.incrementBy(result.deletedForStorageCount)
            }
        }
    }
}

object NoOpSendfileQueue : SendFileQueue {
    override fun pushOldestFile() = Unit
    override fun createFile(dropboxTag: String): File = File("/dev/null")
    override fun incrementSendCount(file: File) = Unit

    override val nextFile: StateFlow<File?>
        get() = MutableStateFlow<File?>(null).asStateFlow()
}
