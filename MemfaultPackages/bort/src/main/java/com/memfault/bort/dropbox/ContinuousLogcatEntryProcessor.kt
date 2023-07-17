package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DataScrubber
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.logcat.LogcatLineProcessor
import com.memfault.bort.logcat.NextLogcatCidProvider
import com.memfault.bort.logcat.SelinuxViolationLogcatDetector
import com.memfault.bort.logcat.scrub
import com.memfault.bort.logcat.toAllowedUids
import com.memfault.bort.logcat.update
import com.memfault.bort.logcat.writeTo
import com.memfault.bort.parsers.toLogcatLines
import com.memfault.bort.settings.LogcatCollectionMode.CONTINUOUS
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.ContinuousLogFile
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.LogcatFileUploadPayload
import com.memfault.bort.uploader.PendingFileUploadEntry
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.toKotlinDuration
import okio.ByteString.Companion.toByteString

private const val DROPBOX_ENTRY_TAG = "memfault_clog"

@ContributesMultibinding(SingletonComponent::class)
class ContinuousLogcatEntryProcessor @Inject constructor(
    private val logcatSettings: LogcatSettings,
    private val dataScrubber: DataScrubber,
    private val packageManagerClient: PackageManagerClient,
    private val packageNameAllowList: PackageNameAllowList,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val fileUploadingArea: FileUploadHoldingArea,
    private val kernelOopsDetector: Provider<LogcatLineProcessor>,
    private val selinuxViolationLogcatDetector: SelinuxViolationLogcatDetector,
    @ContinuousLogFile private val tokenBucketStore: TokenBucketStore,
) : EntryProcessor() {
    override val tags: List<String> = listOf(DROPBOX_ENTRY_TAG)

    // Equivalent to what continuous logging produces
    private val continuousLogcatCommand = LogcatCommand(
        format = LogcatFormat.THREADTIME,
        formatModifiers = listOf(
            LogcatFormatModifier.NSEC,
            LogcatFormatModifier.UTC,
            LogcatFormatModifier.YEAR,
            LogcatFormatModifier.UID,
        )
    )

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.takeSimple(key = DROPBOX_ENTRY_TAG, tag = "continuous_log")

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        val stream = entry.inputStream ?: return

        if (!logcatSettings.dataSourceEnabled) {
            return
        }

        if (!allowedByRateLimit()) {
            return
        }

        val packageManagerReport = packageManagerClient.getPackageManagerReport()
        val allowedUids = packageManagerReport.toAllowedUids(packageNameAllowList)

        val kernelOopsDetector = kernelOopsDetector.get()

        val metadata = stream.bufferedReader().useLines { lines ->
            temporaryFileFactory.createTemporaryFile(
                prefix = "logcat", suffix = ".txt"
            ).useFile { file, preventDeletion ->
                file.outputStream().bufferedWriter().use { outputWriter ->
                    val md5 = MessageDigest.getInstance("MD5")
                    val (timeStart, timeEnd) = lines.toLogcatLines(continuousLogcatCommand)
                        .map { it.scrub(dataScrubber, allowedUids) }
                        .onEach {
                            selinuxViolationLogcatDetector.process(it, packageManagerReport)
                            kernelOopsDetector.process(it)
                            it.writeTo(outputWriter)
                            it.update(md5)
                        }
                        .fold(Pair<Instant?, Instant?>(null, null)) { (minTime, maxTime), line ->
                            Pair(minTime ?: line.logTime, line.logTime ?: maxTime)
                        }
                    preventDeletion()
                    ProcessedLogcatFileMetadata(timeStart, timeEnd, file, md5.digest().toByteString().hex())
                }
            }
        }

        if (metadata.timeStart == null || metadata.timeEnd == null) {
            Logger.d("no parsed timestamps, is logcat empty? ignoring.")
            metadata.file.delete()
            return
        }

        val containsOops = kernelOopsDetector.finish(AbsoluteTime(metadata.timeEnd.plusNanos(1)))

        val (cid, nextCid) = nextLogcatCidProvider.rotate()

        val now = combinedTimeProvider.now()

        // We know the exact timestamps and span but timeSpan takes elapsedRealTime rather than instants,
        // so we approximate by checking the difference between the current timestamp and that of logcat
        // timestamp and subtracting that from the current elapsed time.
        val start = now.elapsedRealtime.duration - Duration.between(metadata.timeStart, now.timestamp)
            .toKotlinDuration()
        val end = now.elapsedRealtime.duration - Duration.between(metadata.timeEnd, now.timestamp)
            .toKotlinDuration()

        val fileUploadEntry = PendingFileUploadEntry(
            timeSpan = PendingFileUploadEntry.TimeSpan.from(start, end),
            payload = LogcatFileUploadPayload(
                collectionTime = now,
                command = continuousLogcatCommand.toList(),
                cid = cid,
                nextCid = nextCid,
                containsOops = containsOops,
                collectionMode = CONTINUOUS,
            ),
            file = metadata.file,
        )
        Logger.test("continuous log file uploaded with uuid=$cid")

        fileUploadingArea.add(fileUploadEntry)
    }
}

data class ProcessedLogcatFileMetadata(
    val timeStart: Instant?,
    val timeEnd: Instant?,
    val file: File,
    val md5: String,
)
