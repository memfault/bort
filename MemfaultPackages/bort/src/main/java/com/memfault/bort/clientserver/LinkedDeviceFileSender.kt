package com.memfault.bort.clientserver

import com.memfault.bort.IO
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Sends a file (via UsageReporter) to a Bort instance running on another device.
 */
class LinkedDeviceFileSender @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
    @IO private val ioCoroutineContext: CoroutineContext,
) {
    suspend fun sendFileToLinkedDevice(
        file: File,
        dropboxTag: String,
    ) = withContext(ioCoroutineContext) {
        Logger.test("sendFileToLinkedDevice: $file")
        reporterServiceConnector.connect { getConnection ->
            getConnection().sendFileToLinkedDevice(file, dropboxTag)
        }
    }
}
