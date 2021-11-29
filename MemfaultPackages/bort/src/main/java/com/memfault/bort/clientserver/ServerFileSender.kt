package com.memfault.bort.clientserver

import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.Logger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends a file (via UsageReporter) to a Bort instance running on another device, for upload.
 */
class ServerFileSender(
    private val reporterServiceConnector: ReporterServiceConnector,
) {
    suspend fun sendFileToBortServer(file: File) = withContext(Dispatchers.IO) {
        Logger.test("sendFileToBortServer: $file")
        reporterServiceConnector.connect { getConnection ->
            getConnection().uploadFileToServer(file)
        }
    }
}
