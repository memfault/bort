package com.memfault.bort.logcat

import android.os.Build
import android.os.RemoteException
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.IO
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.settings.LogcatCollectionMode.PERIODIC
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.shared.LogcatBufferId
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

class PeriodicLogcatRunner @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
    private val processExecutor: ProcessExecutor,
    private val bortSystemCapabilities: BortSystemCapabilities,
) {
    private suspend fun runLogcatLocally(
        command: LogcatCommand,
        handleLines: suspend (InputStream) -> Unit,
    ) {
        processExecutor.execute(command.toList()) { handleLines(it) }
    }

    private suspend fun runLogcatUsingReporter(
        command: LogcatCommand,
        timeout: Duration,
        handleLines: suspend (InputStream) -> Unit,
    ) {
        reporterServiceConnector.connect { getClient ->
            getClient().logcatRun(command, timeout) { invocation ->
                invocation.awaitInputStream().map { stream ->
                    stream.use {
                        handleLines(stream)
                    }
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

    suspend fun runLogcat(
        command: LogcatCommand,
        timeout: Duration,
        sdkVersion: Int,
        handleLines: suspend (InputStream) -> Unit,
    ) {
        if (sdkVersion >= SDK_VERSION_LOGCAT_NEEDS_SYSTEM_UID &&
            bortSystemCapabilities.reporterServiceVersion.get() != null
        ) {
            Logger.d("Running logcat using reporter")
            runLogcatUsingReporter(command, timeout, handleLines)
        } else {
            Logger.d("Running logcat using local")
            runLogcatLocally(command, handleLines)
        }
    }

    companion object {
        private const val SDK_VERSION_LOGCAT_NEEDS_SYSTEM_UID = 33
    }
}

class PeriodicLogcatCollector @Inject constructor(
    private val logcatSettings: LogcatSettings,
    private val nextLogcatStartTimeProvider: NextLogcatStartTimeProvider,
    private val periodicLogcatRunner: PeriodicLogcatRunner,
    private val logcatProcessor: LogcatProcessor,
    @IO private val ioDispatcher: CoroutineContext,
) {
    suspend fun collect() {
        val command = logcatCommand(
            since = nextLogcatStartTimeProvider.nextStart,
            filterSpecs = logcatSettings.filterSpecs,
        )

        runLogcat(command = command)
    }

    private fun logcatCommand(
        since: BaseAbsoluteTime,
        filterSpecs: List<LogcatFilterSpec>,
    ) =
        LogcatCommand(
            dumpAndExit = true,
            dividers = true,
            buffers = listOf(LogcatBufferId.ALL),
            // When using -v UTC, the argument to -t is expected to be in the UTC timezone.
            recentSince = LocalDateTime.ofEpochSecond(
                since.timestamp.epochSecond,
                since.timestamp.nano,
                ZoneOffset.UTC,
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
        command: LogcatCommand,
    ) = withContext(ioDispatcher) {
        try {
            val handler: suspend (InputStream) -> Unit = { stream ->
                val result = logcatProcessor.process(stream, command, PERIODIC)
                result?.let { nextLogcatStartTimeProvider.nextStart = AbsoluteTime(it.timeEnd) }
            }
            periodicLogcatRunner.runLogcat(
                command = command,
                timeout = logcatSettings.commandTimeout,
                sdkVersion = Build.VERSION.SDK_INT,
                handleLines = handler,
            )
        } catch (e: RemoteException) {
            Logger.w("Unable to connect to ReporterService to run logcat")
            null
        } catch (e: Exception) {
            Logger.w("Failed to process logcat", e)
            null
        }
    }
}
