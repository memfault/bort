package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.metricForTraceTag
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.time.toAbsoluteTime
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import com.memfault.bort.uploader.HandleEventOfInterest
import java.io.File
import javax.inject.Inject

interface UploadingEntryProcessorDelegate {
    val tags: List<String>

    val debugTag: String

    val tokenBucketStore: TokenBucketStore

    suspend fun createMetadata(
        entryInfo: EntryInfo,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata

    suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo = EntryInfo(entry.tag)

    fun isTraceEntry(entry: DropBoxManager.Entry): Boolean = true
}

data class EntryInfo(
    val tokenBucketKey: String,
    val packageName: String? = null,
    val packages: List<FileUploadPayload.Package> = emptyList(),
)

class UploadingEntryProcessor<T : UploadingEntryProcessorDelegate> @Inject constructor(
    private val delegate: T,
    private val tempFileFactory: TemporaryFileFactory,
    private val enqueueUpload: EnqueueUpload,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val builtinMetricsStore: BuiltinMetricsStore,
    private val packageNameAllowList: PackageNameAllowList,
    private val handleEventOfInterest: HandleEventOfInterest,
    private val combinedTimeProvider: CombinedTimeProvider,
) : EntryProcessor() {
    override val tags: List<String>
        get() = delegate.tags

    private fun allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean =
        delegate.tokenBucketStore.edit { map ->
            val bucket = map.upsertBucket(tokenBucketKey) ?: return@edit false
            bucket.take(tag = "dropbox_$tag")
        }

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        tempFileFactory.createTemporaryFile(entry.tag, ".txt").useFile { tempFile, preventDeletion ->
            tempFile.outputStream().use { outStream ->
                val copiedBytes = entry.inputStream.use {
                    inStream ->
                    inStream ?: return@useFile
                    inStream.copyTo(outStream)
                }
                if (copiedBytes == 0L) return@useFile
            }

            val info = delegate.getEntryInfo(entry, tempFile)
            if (info.packageName !in packageNameAllowList) {
                return
            }

            builtinMetricsStore.increment(metricForTraceTag(entry.tag))

            if (!allowedByRateLimit(info.tokenBucketKey, entry.tag)) {
                return
            }

            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            val now = bootRelativeTimeProvider.now()
            enqueueUpload.enqueue(
                tempFile,
                DropBoxEntryFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    softwareVersion = deviceInfo.softwareVersion,
                    cidReference = nextLogcatCidProvider.cid,
                    metadata = delegate.createMetadata(
                        info,
                        entry.tag,
                        fileTime,
                        entry.timeMillis.toAbsoluteTime(),
                        now,
                    )
                ),
                debugTag = delegate.debugTag,
                collectionTime = combinedTimeProvider.now(),
            )

            preventDeletion()

            // Only consider trace entries as "events of interest" for log collection purposes:
            if (delegate.isTraceEntry(entry)) {
                handleEventOfInterest.handleEventOfInterest(now)
            }
        }
    }
}
