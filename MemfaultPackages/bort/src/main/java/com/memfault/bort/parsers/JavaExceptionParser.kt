package com.memfault.bort.parsers

import com.memfault.bort.shared.consume
import java.io.InputStream

/**
 * NOTE: This parser does not parse much at all!
 * All it needs to do today, is grab the list of (unparsed) functions in the stack trace.
 * Frames from any underlying ("Caused by:") or supressed exceptions are included as they
 * appear in the text. For the purpose of generating a signature from the stack trace,
 * this is fine.
 */
data class JavaException(
    val unparsedStackFrames: List<String>,
)

class JavaExceptionParser(val inputStream: InputStream) {
    fun parse(): JavaException {
        val lines = Lines(inputStream.bufferedReader().lineSequence().asIterable())
        dropActivityManagerDropBoxMetadata(lines)
        return JavaException(
            unparsedStackFrames = lines.mapNotNull {
                FRAME_REGEX.matchEntire(it)?.groupValues?.get(1)
            },
        )
    }

    private fun dropActivityManagerDropBoxMetadata(lines: Lines) =
        lines.until(consumeMatch = true) { it.isEmpty() }.use { it.consume() }
}

private val FRAME_REGEX = Regex("""^\s*at (.*)""")
