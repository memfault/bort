package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.AndroidPackage
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.clientserver.MarMetadata.DropBoxMarMetadata
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.dropBoxTagCounter
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.metrics.metricForTraceTag
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

    suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo

    fun scrub(inputFile: File, tag: String): File = inputFile
}

data class EntryInfo(
    val tokenBucketKey: String,
    val packageName: String? = null,
    val packages: List<AndroidPackage> = emptyList(),
    val ignored: Boolean,
    val allowedByRateLimit: Boolean,
    val isTrace: Boolean,
    val isCrash: Boolean,
    val crashTag: String?,
)

class UploadingEntryProcessor<T : UploadingEntryProcessorDelegate> @Inject constructor(
    private val delegate: T,
    private val tempFileFactory: TemporaryFileFactory,
    private val enqueueUpload: EnqueueUpload,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
    private val builtinMetricsStore: BuiltinMetricsStore,
    private val packageNameAllowList: PackageNameAllowList,
    private val handleEventOfInterest: HandleEventOfInterest,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val crashHandler: CrashHandler,
) : EntryProcessor() {
    override val tags: List<String>
        get() = delegate.tags

    override suspend fun process(entry: DropBoxManager.Entry) {
        tempFileFactory.createTemporaryFile(entry.tag, ".txt").useFile { tempFile, preventDeletion ->
            tempFile.outputStream().use { outStream ->
                val copiedBytes = entry.inputStream.use { inStream ->
                    inStream ?: return@useFile
                    inStream.copyTo(outStream)
                }
                if (copiedBytes == 0L) return@useFile
            }

            val info = delegate.getEntryInfo(entry, tempFile)
            if (info.ignored) {
                return
            }
            if (info.packageName !in packageNameAllowList) {
                return
            }

            builtinMetricsStore.increment(metricForTraceTag(entry.tag))

            val fileTime = entry.timeMillis.toAbsoluteTime()

            // The crash rate should be incremented even if this dropbox trace would be rate limited.
            if (info.isCrash) {
                crashHandler.onCrash(componentName = info.packageName, crashTimestamp = fileTime.timestamp)

                info.crashTag?.let { crashTag ->
                    dropBoxTagCounter(crashTag).increment()
                }
            }

            if (!info.allowedByRateLimit) {
                return
            }

            val fileToUpload = delegate.scrub(tempFile, entry.tag)

            val now = bootRelativeTimeProvider.now()
            enqueueUpload.enqueue(
                file = fileToUpload,
                metadata = DropBoxMarMetadata(
                    entryFileName = fileToUpload.name,
                    tag = entry.tag,
                    entryTime = fileTime,
                    timezone = TimezoneWithId.deviceDefault,
                    cidReference = nextLogcatCidProvider.cid,
                    packages = info.packages,
                    fileTime = fileTime,
                ),
                collectionTime = combinedTimeProvider.now(),
            )

            preventDeletion()

            // Only consider trace entries as "events of interest" for log collection purposes:
            if (info.isTrace) {
                handleEventOfInterest.handleEventOfInterest(now)
            }
        }
    }
}

internal fun TokenBucketStore.allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean =
    takeSimple(key = tokenBucketKey, tag = "dropbox_$tag")
