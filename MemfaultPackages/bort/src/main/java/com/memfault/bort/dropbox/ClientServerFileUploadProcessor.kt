package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.clientserver.MarFileWriter.Companion.MAR_EXTENSION
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.tokenbucket.MarDropbox
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class)
class ClientServerFileUploadProcessor @Inject constructor(
    private val tempFileFactory: TemporaryFileFactory,
    private val marHoldingArea: MarFileHoldingArea,
    @MarDropbox private val tokenBucketStore: TokenBucketStore,
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

        tempFileFactory.createTemporaryFile(entry.tag, ".$MAR_EXTENSION").useFile { tempFile, preventDeletion ->
            entry.inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return

            // Only sampled files are sent over the client/server link (that decision is made by the other device).
            marHoldingArea.addSampledMarFileDirectlyFromOtherDevice(tempFile)
            preventDeletion()
        }
    }

    companion object {
        private const val MAR_FILE_TAG = "mar_file"
    }
}
