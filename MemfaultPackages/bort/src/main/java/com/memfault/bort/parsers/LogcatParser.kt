package com.memfault.bort.parsers

import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.LogcatPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * After running logcat, Bort will need to pull out the timestamp of the last log line.
 * This timestamp + 1 nsec, will be used as the starting point for the next logcat invocation.
 */
data class LogcatLine(
    val logTime: Instant? = null,
    val uid: Int? = null,
    val lineUpToTag: String? = null,
    val message: String? = null,
    val buffer: String? = null,
    val tag: String? = null,
    val priority: LogcatPriority? = null,
    val separator: Boolean = false,
)

class LogcatParser(
    val lines: Flow<String>,
    val command: LogcatCommand,
    val uidDecoder: (String) -> Int = android.os.Process::getUidForName,
) {
    private var buffer: String? = null

    private val uidCache: MutableMap<String, Int?> = mutableMapOf()

    init {
        // The parsing assumes these formatting flags are used:
        check(
            command.format == LogcatFormat.THREADTIME &&
                command.formatModifiers.containsAll(
                    listOf(
                        LogcatFormatModifier.NSEC,
                        LogcatFormatModifier.UTC,
                        LogcatFormatModifier.YEAR,
                        LogcatFormatModifier.UID,
                    ),
                ),
        ) {
            "Unsupported logcat command: $command"
        }
    }

    private fun parseSeparator(line: String): LogcatLine {
        // logcat source code: https://cs.android.com/android/_/android/platform/system/logging/+/f45bd38d476ae2fafa34edc4445b9b6a346803f0:logcat/logcat.cpp;l=241-242;drc=f45bd38d476ae2fafa34edc4445b9b6a346803f0
        // Example separators:
        // --------- beginning of kernel
        // --------- switch to main
        return if (line.startsWith("-")) {
            buffer = line.substringAfterLast(" ", "").ifEmpty { buffer }
            LogcatLine(message = line, buffer = buffer, separator = true)
        } else {
            LogcatLine(message = line, buffer = buffer)
        }
    }

    fun parse(): Flow<LogcatLine> {
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn Z")
        return flow {
            var pendingSeparator: LogcatLine? = null

            lines.collect { line ->
                val parsedLine = parseLine(line, timeFormatter)
                if (parsedLine.separator) {
                    pendingSeparator = parsedLine
                } else {
                    pendingSeparator?.let { emit(it) }
                    pendingSeparator = null
                    emit(parsedLine)
                }
            }

            pendingSeparator?.let { emit(it) }
        }
    }

    private fun parseLine(line: String, timeFormatter: DateTimeFormatter): LogcatLine =
        LINE_REGEX.matchEntire(line)?.let { result ->
            val time = result.groups.get(TIME_GROUP)?.value?.let {
                val time = try {
                    ZonedDateTime.parse(it, timeFormatter).toInstant()
                } catch (e: DateTimeParseException) {
                    null
                }
                time
            }

            val uid = result.groups.get(UID_GROUP)?.value?.let { parseUid(it) }
            // Note: named groups can't contain other named groups, that's why this one is not named:
            //  java.util.regex.PatternSyntaxException: named capturing group is missing trailing '>' near index 6
            val upToTag = result.groups.get(UP_TO_TAG_GROUP)?.value
            val msg = result.groups.get(MSG_GROUP)?.value
            val tag = result.groups.get(TAG_GROUP)?.value
            val priority = result.groups.get(PRIORITY_GROUP)?.value?.let { LogcatPriority.getByCliValue(it) }
            LogcatLine(
                logTime = time,
                uid = uid,
                lineUpToTag = upToTag,
                message = msg,
                buffer = buffer,
                tag = tag,
                priority = priority,
            )
        } ?: parseSeparator(line)

    private fun parseUid(uid: String): Int? =
        uid.toIntOrNull() ?: uidCache.computeIfAbsent(uid) {
            uidDecoder(uid).let {
                if (it == -1) null else it
            }
        }
}

fun Flow<String>.toLogcatLines(command: LogcatCommand): Flow<LogcatLine> =
    LogcatParser(this, command).parse()

private val LINE_REGEX = Regex(
    """^(([0-9]+-[0-9]+-[0-9]+ [0-9]+:[0-9]+:[0-9]+\.[0-9]+ \+[0-9]+)""" +
        """\s+([a-zA-Z_\-0-9]+)""" +
        """\s+[^:]+\s+([EWIDVFS])\s+(.+?)?\s*:""" + // everything up to tag:
        """\s+)(.*)$""",
)

private const val UP_TO_TAG_GROUP = 1
private const val TIME_GROUP = 2
private const val UID_GROUP = 3
private const val PRIORITY_GROUP = 4
private const val TAG_GROUP = 5
private const val MSG_GROUP = 6
