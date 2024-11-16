package com.memfault.bort.logcat

import android.os.Process
import com.memfault.bort.DataScrubber
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.logcat.LogcatLineProcessorResult.ContainsOops
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.parsers.toLogcatLines
import com.memfault.bort.settings.LogcatCollectionMode
import com.memfault.bort.settings.LogcatCollectionMode.CONTINUOUS
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.FileUploadHoldingArea
import com.memfault.bort.uploader.LogcatFileUploadPayload
import com.memfault.bort.uploader.PendingFileUploadEntry
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.BufferedWriter
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.toKotlinDuration

/**
 * Common logcat processing between continuous/periodic collection.
 */
class LogcatProcessor @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val lineProcessorFactories: InjectSet<LogcatLineProcessor.Factory>,
    private val packageManagerClient: PackageManagerClient,
    private val packageNameAllowList: PackageNameAllowList,
    private val dataScrubber: DataScrubber,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val fileUploadingArea: FileUploadHoldingArea,
) {
    suspend fun process(
        inputStream: InputStream,
        command: LogcatCommand,
        collectionMode: LogcatCollectionMode,
    ): LogcatProcessorResult? {
        val packageManagerReport = packageManagerClient.getPackageManagerReport()
        val allowedUids = packageManagerReport.toAllowedUids(packageNameAllowList)
        val lineProcessors = lineProcessorFactories.map { it.create() }

        temporaryFileFactory.createTemporaryFile(
            prefix = "logcat-scrubbed",
            suffix = ".txt",
        ).useFile { scrubbedFile, preventDeletion ->
            val (timeStart, initialTimeEnd) = scrubbedFile.outputStream().bufferedWriter().use { scrubbedWriter ->
                inputStream.use { input ->
                    input.bufferedReader().useLines { lines ->
                        lines.asFlow().toLogcatLines(command)
                            .onEach { line -> lineProcessors.forEach { it.process(line, packageManagerReport) } }
                            .map { it.scrub(dataScrubber, allowedUids) }
                            .onEach { it.writeTo(scrubbedWriter) }
                            .fold(Pair<Instant?, Instant?>(null, null)) { (minTime, maxTime), line ->
                                Pair(minTime ?: line.logTime, line.logTime ?: maxTime)
                            }
                    }
                }
            }
            if (timeStart == null || initialTimeEnd == null) {
                Logger.d("no parsed timestamps, is logcat empty? ignoring.")
                return null
            }
            val timeEnd = initialTimeEnd.plusNanos(1)
            val lineProcessorResults = lineProcessors.flatMap { it.finish(AbsoluteTime(timeEnd)) }
            preventDeletion()

            // We know the exact timestamps and span but timeSpan takes elapsedRealTime rather than instants,
            // so we approximate by checking the difference between the current timestamp and that of logcat
            // timestamp and subtracting that from the current elapsed time.
            val now = combinedTimeProvider.now()
            val start = now.elapsedRealtime.duration - Duration.between(timeStart, now.timestamp)
                .toKotlinDuration()
            val end = now.elapsedRealtime.duration - Duration.between(timeEnd, now.timestamp)
                .toKotlinDuration()

            val (cid, nextCid) = nextLogcatCidProvider.rotate()

            val fileUploadEntry = PendingFileUploadEntry(
                timeSpan = PendingFileUploadEntry.TimeSpan.from(start, end),
                payload = LogcatFileUploadPayload(
                    collectionTime = now,
                    command = command.toList(),
                    cid = cid,
                    nextCid = nextCid,
                    containsOops = lineProcessorResults.contains(ContainsOops),
                    collectionMode = collectionMode,
                ),
                file = scrubbedFile,
            )
            if (collectionMode == CONTINUOUS) {
                Logger.test("continuous log file uploaded with uuid=$cid")
            }

            fileUploadingArea.add(fileUploadEntry)
            return LogcatProcessorResult(timeStart = timeStart, timeEnd = timeEnd)
        }
    }
}

data class LogcatProcessorResult(
    val timeStart: Instant,
    val timeEnd: Instant,
)

internal fun PackageManagerReport.toAllowedUids(allowList: PackageNameAllowList): Set<Int> =
    packages.filter { it.id in allowList }
        .mapNotNull { it.userId }
        .toSet()

internal fun LogcatLine.scrub(
    scrubber: DataScrubber,
    allowedUids: Set<Int>,
) = copy(
    message = message?.let { msg ->
        when {
            uid != null && uid >= Process.FIRST_APPLICATION_UID && allowedUids.isNotEmpty() && uid !in allowedUids ->
                scrubber.scrubEntirely(msg)

            else -> scrubber(msg)
        }
    },
)

internal fun LogcatLine.writeTo(writer: BufferedWriter) {
    lineUpToTag?.let { writer.write(it) }
    message?.let { writer.write(it) }
    writer.newLine()
}
