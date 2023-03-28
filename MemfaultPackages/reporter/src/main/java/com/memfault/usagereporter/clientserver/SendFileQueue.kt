package com.memfault.usagereporter.clientserver

import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.ReporterSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exposes the next file to be uploaded, via [nextFile].
 *
 * Call [pushOldestFile] after any file addition or deletion.
 */
interface SendFileQueue {
    fun pushOldestFile()
    val nextFile: StateFlow<File?>
}

class RealSendfileQueue(
    private val uploadDirectory: File,
    private val reporterSettings: ReporterSettings,
) : SendFileQueue {
    private val _nextFile = MutableStateFlow<File?>(null)
    override val nextFile = _nextFile.asStateFlow()
    private val queuedDeletedMetric = Reporting.report().counter(
        name = "sendfile_queue_deleted",
        internal = true,
    )

    private fun cleanup() {
        val result =
            cleanupFiles(dir = uploadDirectory, maxDirStorageBytes = reporterSettings.maxFileTransferStorageBytes)
        if (result.deletedForStorageCount > 0) {
            Logger.d(
                "Deleted ${result.deletedForStorageCount} files queued to send to remote device, " +
                    "to stay under storage limit."
            )
            queuedDeletedMetric.incrementBy(result.deletedForStorageCount)
        }
    }

    // Note: tried using a FileObserver for this (to notify us when the filesystem changes) but it did not work
    // reliably.
    override fun pushOldestFile() {
        cleanup()
        val oldestFile = uploadDirectory.listFiles()?.minByOrNull { it.lastModified() }
        _nextFile.value = oldestFile
    }
}

object NoOpSendfileQueue : SendFileQueue {
    override fun pushOldestFile() = Unit

    override val nextFile: StateFlow<File?>
        get() = MutableStateFlow<File?>(null).asStateFlow()
}
