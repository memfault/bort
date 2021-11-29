package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadToken
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.fileExt.md5Hex
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClientServerFileUploadProcessor(
    private val tempFileFactory: TemporaryFileFactory,
    private val enqueueUpload: EnqueueUpload,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val tokenBucketStore: TokenBucketStore,
) : EntryProcessor() {
    override val tags: List<String> = listOf(CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG)

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.edit { map ->
            map.upsertBucket(MAR_FILE_TAG)?.take(tag = "mar_file") ?: false
        }

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        Logger.d("ClientServerFileUploadProcessor")

        if (!allowedByRateLimit()) {
            return
        }

        tempFileFactory.createTemporaryFile(entry.tag, ".mar").useFile { tempFile, preventDeletion ->
            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            entry.inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return

            enqueueUpload.enqueue(
                file = tempFile,
                metadata = MarFileUploadPayload(
                    file = FileUploadToken(
                        md5 = withContext(Dispatchers.IO) {
                            tempFile.md5Hex()
                        },
                        name = tempFile.name,
                    ),
                    hardwareVersion = deviceInfo.hardwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    softwareVersion = deviceInfo.softwareVersion,
                ),
                debugTag = DEBUG_TAG,
                // Not used
                collectionTime = combinedTimeProvider.now(),
            )

            preventDeletion()
        }
    }

    companion object {
        private const val DEBUG_TAG = "MAR_UPLOAD"
        private const val MAR_FILE_TAG = "mar_file"
    }
}
