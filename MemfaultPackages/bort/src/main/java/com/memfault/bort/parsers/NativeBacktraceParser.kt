package com.memfault.bort.parsers

import com.memfault.bort.shared.consume
import java.io.InputStream

class InvalidNativeBacktraceException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

data class NativeBacktrace(val processes: List<Process>) {
    data class Process(val pid: Int, val cmdLine: String?)
}

class NativeBacktraceParser(val inputStream: InputStream) {
    fun parse(): NativeBacktrace {
        val lines = Lines(inputStream.bufferedReader().lineSequence().asIterable())
        val processes = parseProcesses(lines).toList()
        if (processes.isEmpty()) throw InvalidNativeBacktraceException("No processes")
        return NativeBacktrace(processes = processes)
    }

    private fun parseProcesses(lines: Lines): Sequence<NativeBacktrace.Process> = sequence {
        val skipEmpty = {
            lines.until { it.startsWith(PID_LINE_TOKEN) }.consume()
        }
        skipEmpty()
        for (headerLine in lines) {
            val (pid, _) = parseProcessHeader(headerLine)
            val endOfProcessDumpLine: LinePredicate = { line -> line == "----- end $pid -----" }

            lines.until { it.startsWith(PID_LINE_TOKEN) }.use { processLines ->
                processLines.until(endOfProcessDumpLine).use { innerProcessLines ->
                    yield(parseProcess(innerProcessLines, pid))
                }
            }
            skipEmpty()
        }
    }

    private fun parseProcess(lines: Lines, pid: Int): NativeBacktrace.Process {
        val metadata = parseProcessMetadata(lines)
        return NativeBacktrace.Process(
            pid = pid,
            cmdLine = metadata.get(CMD_LINE_KEY),
        )
    }

    companion object {
        fun parseProcessMetadata(lines: Lines): Map<String, String> =
            lines.until { it.isEmpty() }.use { metaLines ->
                metaLines.mapNotNull {
                    try {
                        val (key, value) = it.split(": ", limit = 2)
                        Pair(key, value)
                    } catch (e: IndexOutOfBoundsException) {
                        throw InvalidNativeBacktraceException("Failed to parse process metadata")
                    }
                }
            }.toMap()

        fun parseProcessHeader(line: String): Pair<Int, String> {
            // Read out the pid & timestamp from
            // ----- pid 9735 at 2019-08-21 12:17:22 -----
            val pattern = Regex("""----- pid\s([0-9]+)\sat\s(.+) -----""")
            val (pid, time) = pattern.matchEntire(line)?.destructured
                ?: throw InvalidNativeBacktraceException("Failed to parse process header")

            return Pair(pid.toInt(), time)
        }
    }
}

private const val PID_LINE_TOKEN = "----- pid "
private const val CMD_LINE_KEY = "Cmd line"
