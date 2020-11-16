package com.memfault.bort.parsers

import java.io.BufferedReader
import java.io.InputStream

class InvalidTombstoneException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

data class Tombstone(val pid: Int, val tid: Int, val threadName: String, val processName: String)

class TombstoneParser(val inputStream: InputStream) {
    fun parse(): Tombstone {
        val lines = inputStream.bufferedReader().lineSequence().iterator()
        parsePrologueAndHeader(lines)
        return with(parseThreadHeader(lines)) {
            Tombstone(pid, tid, threadName, processName)
        }
    }

    private data class ThreadHeader(val pid: Int, val tid: Int, val threadName: String, val processName: String)

    private fun parseThreadHeader(lines: Iterator<String>): ThreadHeader {
        for (line in lines) {
            val (pid, tid, threadName, processName) =
                THREAD_HEADER_REGEX.matchEntire(line)?.destructured ?: continue
            return try {
                ThreadHeader(pid.toInt(), tid.toInt(), threadName, processName)
            } catch (e: NumberFormatException) {
                throw InvalidTombstoneException("Thread header failed to parse", e)
            }
        }
        throw InvalidTombstoneException("Failed to find thread header")
    }

    private fun parsePrologueAndHeader(lines: Iterator<String>) {
        for ((index, line) in lines.withIndex()) {
            if (line.startsWith(FILE_START_TOKEN)) return
            if ((index == 0) and line.isBlank()) throw InvalidTombstoneException()
            if ("failed to dump process" in line) throw InvalidTombstoneException()
        }
        throw InvalidTombstoneException()
    }

    private fun BufferedReader.safeReadLine(): String =
        readLine() ?: throw InvalidTombstoneException()
}

private const val FILE_START_TOKEN = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***"
private val THREAD_HEADER_REGEX = Regex("""^pid: (\d+), tid: (\d+), name: (.+) {2}>>> (.+) <<<$""")
