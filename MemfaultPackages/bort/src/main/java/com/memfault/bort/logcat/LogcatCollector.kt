package com.memfault.bort.logcat

import android.os.Process
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
import com.memfault.bort.settings.ConfigValue
import com.memfault.bort.shared.LogcatBufferId
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LogcatCollectorResult(
    val command: LogcatCommand,
    val file: File,
    val cid: LogcatCollectionId,
    val nextCid: LogcatCollectionId,
)

class LogcatCollector(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val nextLogcatStartTimeProvider: NextLogcatStartTimeProvider,
    private val nextLogcatCidProvider: NextLogcatCidProvider,
    private val runLogcat: suspend (outputStream: OutputStream, command: LogcatCommand, timeout: Duration) -> Unit,
    private val filterSpecsConfig: ConfigValue<List<LogcatFilterSpec>>,
    private val dataScrubber: ConfigValue<DataScrubber>,
    private val timeoutConfig: ConfigValue<Duration>,
    private val packageNameAllowList: PackageNameAllowList,
    private val packageManagerClient: PackageManagerClient,
    private val now: () -> BaseAbsoluteTime = AbsoluteTime.Companion::now,
) {
    suspend fun collect(): LogcatCollectorResult? {
        temporaryFileFactory.createTemporaryFile(
            "logcat", suffix = ".txt"
        ).useFile { file, preventDeletion ->
            val command = logcatCommand(
                since = nextLogcatStartTimeProvider.nextStart,
                filterSpecs = filterSpecsConfig(),
            )
            nextLogcatStartTimeProvider.nextStart = runLogcat(
                outputFile = file,
                command = command,
                allowedUids = packageManagerClient.getPackageManagerReport()
                    .toAllowedUids(packageNameAllowList)
            )
            if (file.length() == 0L) {
                return null
            }
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
        val lastLogTime = try {
            outputFile.outputStream().use {
                runLogcat(it, command, timeoutConfig())
            }

            outputFile.bufferedReader().useLines { lines ->
                temporaryFileFactory.createTemporaryFile(
                    prefix = "logcat-scrubbed", suffix = ".txt"
                ).useFile { scrubbedFile, _ ->
                    scrubbedFile.outputStream().bufferedWriter().use { scrubbedWriter ->
                        val scrubber = dataScrubber()
                        lines.toLogcatLines(command)
                            .map { it.scrub(scrubber, allowedUids) }
                            .onEach { it.writeTo(scrubbedWriter) }
                            .asIterable()
                            .last()
                            .logTime
                    }.also { scrubbedFile.renameTo(outputFile) }
                }
            }
        } catch (e: Exception) {
            Logger.w("Failed to process logcat", e)
            null
        }

        lastLogTime?.let {
            AbsoluteTime(it.plusNanos(1))
        } ?: now().also {
            Logger.w("Failed to parse last timestamp from logs, falling back to current time")
        }
    }
}

suspend fun ReporterServiceConnector.runLogcat(
    outputStream: OutputStream,
    command: LogcatCommand,
    timeout: Duration,
) {
    this.connect { getClient ->
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

internal fun PackageManagerReport.toAllowedUids(allowList: PackageNameAllowList): Set<Int> =
    packages.filter { it.id in allowList }
        .mapNotNull { it.userId }
        .toSet()

private fun LogcatLine.scrub(scrubber: DataScrubber, allowedUids: Set<Int>) = copy(
    message = message?.let { msg ->
        when {
            uid != null && uid >= Process.FIRST_APPLICATION_UID && allowedUids.isNotEmpty() && uid !in allowedUids ->
                scrubber.scrubEntirely(msg)
            else -> scrubber(msg)
        }
    }
)

private fun LogcatLine.writeTo(writer: BufferedWriter) {
    lineUpToTag?.let { writer.write(it) }
    message?.let { writer.write(it) }
    writer.newLine()
}
