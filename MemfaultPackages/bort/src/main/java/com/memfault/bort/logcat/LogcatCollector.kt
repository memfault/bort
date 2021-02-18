package com.memfault.bort.logcat

import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.parsers.LogcatParser
import com.memfault.bort.settings.ConfigValue
import com.memfault.bort.shared.LogcatBufferId
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
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
    private val runLogcat: suspend (outputStream: OutputStream, command: LogcatCommand) -> Unit,
    private val filterSpecsConfig: ConfigValue<List<LogcatFilterSpec>>,
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

    private suspend fun runLogcat(outputFile: File, command: LogcatCommand): BaseAbsoluteTime =
        withContext(Dispatchers.IO) {
            outputFile.outputStream().use {
                runLogcat(it, command)
            }
            outputFile.inputStream().use {
                try {
                    LogcatParser(it, command).parse().lastLogTime
                } catch (e: Exception) {
                    null
                }?.let { lastLogTime ->
                    AbsoluteTime(lastLogTime.plusNanos(1))
                } ?: now().also {
                    Logger.w("Failed to parse last timestamp from logs, falling back to current time")
                }
            }
        }
}

suspend fun ReporterServiceConnector.runLogcat(outputStream: OutputStream, command: LogcatCommand) {
    this.connect { getClient ->
        getClient().logcatRun(command) { invocation ->
            invocation.awaitInputStream().map { stream ->
                stream.copyTo(outputStream)
            }.andThen {
                invocation.awaitResponse().toErrorIf({ it.exitCode != 0 }) {
                    Exception("Remote error: $it")
                }
            }
        }
    } onFailure {
        throw it
    }
}
