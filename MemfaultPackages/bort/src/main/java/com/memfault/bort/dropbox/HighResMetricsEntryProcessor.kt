package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.metrics.HeartbeatReportCollector
import com.memfault.bort.settings.HighResMetricsEnabled
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.tokenbucket.HighResMetricsFile
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

private const val DROPBOX_ENTRY_TAG = "memfault_high_res"

@ContributesMultibinding(SingletonComponent::class)
class HighResMetricsEntryProcessor @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    @HighResMetricsFile private val tokenBucketStore: TokenBucketStore,
    private val highResMetricsEnabled: HighResMetricsEnabled,
    private val heartbeatReportCollector: HeartbeatReportCollector,
) : EntryProcessor() {
    override val tags: List<String> = listOf(DROPBOX_ENTRY_TAG)

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.edit { map ->
            map.upsertBucket(DROPBOX_ENTRY_TAG)?.take(tag = "high_res") ?: false
        }

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        if (!highResMetricsEnabled()) {
            return
        }

        if (!allowedByRateLimit()) {
            return
        }

        temporaryFileFactory.createTemporaryFile(entry.tag, "txt").useFile { tempFile, preventDeletion ->
            tempFile.outputStream().use { outStream ->
                val copiedBytes = entry.inputStream.use { inStream ->
                    inStream ?: return@useFile
                    inStream.copyTo(outStream)
                }
                if (copiedBytes == 0L) return@useFile
            }
            preventDeletion()
            heartbeatReportCollector.handleHighResMetricsFile(tempFile)
        }
    }
}
