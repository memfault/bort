package com.memfault.bort.parsers

import java.io.InputStream

class InvalidTombstoneException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

data class Tombstone(val pid: Int, val tid: Int, val threadName: String, val processName: String)

class TombstoneParser(val inputStream: InputStream) {
    fun parse(): Tombstone {
        val lines = Lines(inputStream.bufferedReader().lineSequence().asIterable())
        parsePrologueAndHeader(lines)
        return with(parseThreadHeader(lines)) {
            Tombstone(pid, tid, threadName, processName)
        }
    }

    private data class ThreadHeader(
        val pid: Int,
        val tid: Int,
        val threadName: String,
        val processName: String,
    )

    private fun parseThreadHeader(lines: Lines): ThreadHeader {
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

    private fun parsePrologueAndHeader(lines: Lines) {
        lines.until { it.startsWith(FILE_START_TOKEN) }.use { prologueLines ->
            for (line in prologueLines) {
                if ("failed to dump process" in line) throw InvalidTombstoneException()
            }
        }
        if (lines.peek() == null) throw InvalidTombstoneException()
    }
}

private const val FILE_START_TOKEN = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***"
private val THREAD_HEADER_REGEX = Regex("""^pid: (\d+), tid: (\d+), name: (.+) {2}>>> (.+) <<<$""")
