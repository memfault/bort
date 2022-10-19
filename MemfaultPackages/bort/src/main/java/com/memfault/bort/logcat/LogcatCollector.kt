package com.memfault.bort.logcat

import android.os.Process
import android.os.RemoteException
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.DataScrubber
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.PackageNameAllowList
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.parsers.toLogcatLines
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.shared.LogcatBufferId
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.AbsoluteTimeProvider
import com.memfault.bort.time.BaseAbsoluteTime
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LogcatCollectorResult(
    val command: LogcatCommand,
    val file: File,
    val cid: LogcatCollectionId,
    val nextCid: LogcatCollectionId,
)

class LogcatRunner @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
) {
    suspend fun runLogcat(
        outputStream: OutputStream,
        command: LogcatCommand,
        timeout: Duration,
    ) {
        reporterServiceConnector.connect { getClient ->
            getClient().logcatRun(command, timeout) { invocation ->
                invocation.awaitInputStream().map { stream ->
                    stream.copyTo(outputStream)
                }.andThen {
                    invocation.awaitResponse(timeout).toErrorIf({ it.exitCode != 0 }) {
                        Exception("Remote error: $it")
                    }
                }
            }
        } onFailure {
            throw it
        }
    }
}

class LogcatCollector @Inject constructor(
    private val logcatSettings: LogcatSettings,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val nextLogcatStartTimeProvider: NextLogcatStartTimeProvider,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val logcatRunner: LogcatRunner,
    private val now: AbsoluteTimeProvider,
    private val kernelOopsDetector: Provider<LogcatLineProcessor>,
    private val packageManagerClient: PackageManagerClient,
    private val packageNameAllowList: PackageNameAllowList,
    private val dataScrubber: DataScrubber,
) {
    suspend fun collect(): LogcatCollectorResult {
        temporaryFileFactory.createTemporaryFile(
            "logcat", suffix = ".txt"
        ).useFile { file, preventDeletion ->
            val command = logcatCommand(
                since = nextLogcatStartTimeProvider.nextStart,
                filterSpecs = logcatSettings.filterSpecs,
            )
            nextLogcatStartTimeProvider.nextStart = runLogcat(
                outputFile = file,
                command = command,
                allowedUids = packageManagerClient.getPackageManagerReport()
                    .toAllowedUids(packageNameAllowList)
            )
            val (cid, nextCid) = nextLogcatCidProvider.rotate()
            preventDeletion()
            return LogcatCollectorResult(
                command = command,
                file = file,
                cid = cid,
                nextCid = nextCid,
            )
        }
    }

    private fun logcatCommand(since: BaseAbsoluteTime, filterSpecs: List<LogcatFilterSpec>) =
        LogcatCommand(
            dumpAndExit = true,
            dividers = true,
            buffers = listOf(LogcatBufferId.ALL),
            // When using -v UTC, the argument to -t is expected to be in the UTC timezone.
            recentSince = LocalDateTime.ofEpochSecond(
                since.timestamp.epochSecond,
                since.timestamp.nano,
                ZoneOffset.UTC
            ),
            format = LogcatFormat.THREADTIME,
            formatModifiers = listOf(
                // Nanosecond precision is needed in order to properly determine the last log time, to which 1 nsec is
                // added to determine the starting point for the next hunk of logs.
                LogcatFormatModifier.NSEC,
                LogcatFormatModifier.PRINTABLE,
                LogcatFormatModifier.UID,
                LogcatFormatModifier.UTC,
                LogcatFormatModifier.YEAR,
            ),
            filterSpecs = filterSpecs,
        )

    private suspend fun runLogcat(
        outputFile: File,
        command: LogcatCommand,
        allowedUids: Set<Int>,
    ) = withContext(Dispatchers.IO) {
        val kernelOopsDetector = kernelOopsDetector.get()

        val lastLogTime = try {
            outputFile.outputStream().use {
                logcatRunner.runLogcat(it, command, logcatSettings.commandTimeout)
            }

            outputFile.bufferedReader().useLines { lines ->
                temporaryFileFactory.createTemporaryFile(
                    prefix = "logcat-scrubbed", suffix = ".txt"
                ).useFile { scrubbedFile, preventScrubbedDeletion ->
                    scrubbedFile.outputStream().bufferedWriter().use { scrubbedWriter ->
                        val scrubber = dataScrubber
                        lines.toLogcatLines(command)
                            .onEach { kernelOopsDetector.process(it) }
                            .map { it.scrub(scrubber, allowedUids) }
                            .onEach { it.writeTo(scrubbedWriter) }
                            .asIterable()
                            .lastOrNull { it.logTime != null }
                            ?.logTime
                    }.also {
                        preventScrubbedDeletion()
                        scrubbedFile.renameTo(outputFile)
                    }
                }
            }
        } catch (e: RemoteException) {
            Logger.w("Unable to connect to ReporterService to run logcat")
            null
        } catch (e: Exception) {
            Logger.w("Failed to process logcat", e)
            null
        }

        val lastLogTimeOrFallback = lastLogTime?.let {
            AbsoluteTime(it.plusNanos(1))
        } ?: now().also {
            Logger.w("Failed to parse last timestamp from logs, falling back to current time")
        }

        lastLogTimeOrFallback.also {
            kernelOopsDetector.finish(it)
        }
    }
}

internal fun PackageManagerReport.toAllowedUids(allowList: PackageNameAllowList): Set<Int> =
    packages.filter { it.id in allowList }
        .mapNotNull { it.userId }
        .toSet()

internal fun LogcatLine.scrub(scrubber: DataScrubber, allowedUids: Set<Int>) = copy(
    message = message?.let { msg ->
        when {
            uid != null && uid >= Process.FIRST_APPLICATION_UID && allowedUids.isNotEmpty() && uid !in allowedUids ->
                scrubber.scrubEntirely(msg)
            else -> scrubber(msg)
        }
    }
)

internal fun LogcatLine.writeTo(writer: BufferedWriter) {
    lineUpToTag?.let { writer.write(it) }
    message?.let { writer.write(it) }
    writer.newLine()
}

internal fun LogcatLine.update(digest: MessageDigest) {
    lineUpToTag?.let { digest.update(it.toByteArray()) }
    message?.let { digest.update(it.toByteArray()) }
    digest.update('\n'.code.toByte())
}
