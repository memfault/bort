package com.memfault.usagereporter.clientserver

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
    private val uploadDirectory: File
) : SendFileQueue {
    private val _nextFile = MutableStateFlow<File?>(null)
    override val nextFile = _nextFile.asStateFlow()

    private fun cleanup() {
        // TODO MFLT-4900 remove oldest files if over storage limit.
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
