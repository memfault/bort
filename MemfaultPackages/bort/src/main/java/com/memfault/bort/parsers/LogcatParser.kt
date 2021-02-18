package com.memfault.bort.parsers

import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import java.io.InputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * After running logcat, Bort will need to pull out the timestamp of the last log line.
 * This timestamp + 1 nsec, will be used as the starting point for the next logcat invocation.
 */
data class LogcatReport(
    val lastLogTime: Instant?,
)

class LogcatParser(val inputStream: InputStream, val command: LogcatCommand) {
    fun parse(): LogcatReport {
        // The parsing assumes these formatting flags are used:
        check(
            command.format == LogcatFormat.THREADTIME && command.formatModifiers.containsAll(
                listOf(
                    LogcatFormatModifier.NSEC,
                    LogcatFormatModifier.UTC,
                    LogcatFormatModifier.YEAR,
                )
            )
        ) {
            "Unsupported logcat command: $command"
        }
        val lastLogTime = inputStream.bufferedReader().lineSequence().asIterable().lastOrNull()?.let { lastLine ->
            TIME_REGEX.find(lastLine)?.groupValues?.get(1)?.let {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn Z")
                try {
                    ZonedDateTime.parse(it, formatter).toInstant()
                } catch (e: DateTimeParseException) {
                    null
                }
            }
        }
        return LogcatReport(lastLogTime = lastLogTime)
    }
}

private val TIME_REGEX = Regex("""^([0-9]+-[0-9]+-[0-9]+ [0-9]+:[0-9]+:[0-9]+\.[0-9]+ \+[0-9]+)""")
