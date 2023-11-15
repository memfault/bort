package com.memfault.bort.clientserver

import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Sends a file (via UsageReporter) to a Bort instance running on another device.
 */
class LinkedDeviceFileSender @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
) {
    suspend fun sendFileToLinkedDevice(file: File, dropboxTag: String) = withContext(Dispatchers.IO) {
        Logger.test("sendFileToLinkedDevice: $file")
        reporterServiceConnector.connect { getConnection ->
            getConnection().sendFileToLinkedDevice(file, dropboxTag)
        }
    }
}
