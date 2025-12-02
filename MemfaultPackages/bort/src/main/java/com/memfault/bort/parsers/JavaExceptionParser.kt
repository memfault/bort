package com.memfault.bort.parsers

/**
 * NOTE: This parser does not parse much at all!
 * All it needs to do today, is grab the list of (unparsed) functions in the stack trace.
 * Frames from any underlying ("Caused by:") or suppressed exceptions are included as they
 * appear in the text. For the purpose of generating a signature from the stack trace,
 * this is fine.
 */
data class JavaException(
    val packageName: String?,
    val signatureLines: List<String>,
    val exceptionClass: String?,
    val exceptionMessage: String?,
)

class JavaExceptionParser(
    private val linesSequence: Sequence<String>,
) {
    fun parse(): JavaException {
        var packageName: String? = null
        var topLevelExceptionClass: String? = null
        var topLevelExceptionMessage: String? = null
        val lines = linesSequence.iterator()
        val signatureLines = mutableListOf<String>()

        fun parseMetadata(line: String): Boolean {
            if (packageName == null) {
                packageName = AmMetadataHeader.parsePackage(line)
            }
            return AmMetadataHeader.notMetadataLine(line)
        }

        fun parseStacktrace(line: String): Boolean {
            val classMessageMatch = CLASS_MESSAGE_REGEX.matchEntire(line)
            val frameMatch = FRAME_REGEX.matchEntire(line)
            if (line.isBlank()) {
                return false
            } else if (frameMatch != null) {
                signatureLines.add(frameMatch.groupValues[1].trim())
                return false
            } else if (classMessageMatch != null) {
                val exceptionClass = classMessageMatch.groupValues[1].trim()
                val exceptionMessage = classMessageMatch.groupValues[2].trim()

                // Don't use the exception message in the signature - it can vary too much (such as with numbers).
                signatureLines.add(exceptionClass)

                if (topLevelExceptionClass == null) {
                    topLevelExceptionClass = exceptionClass
                }
                if (topLevelExceptionMessage == null) {
                    topLevelExceptionMessage = exceptionMessage
                }
                return false
            } else {
                return true
            }
        }

        var parser: (String) -> Boolean = ::parseMetadata

        for (line in lines) {
            val stop = parser(line)
            if (parser == ::parseMetadata && stop) {
                parser = ::parseStacktrace
                parser(line)
            } else if (parser == ::parseStacktrace && stop) {
                break
            }
        }

        return JavaException(
            packageName = packageName,
            signatureLines = signatureLines,
            exceptionClass = topLevelExceptionClass,
            exceptionMessage = topLevelExceptionMessage,
        )
    }
}

private val CLASS_MESSAGE_REGEX = Regex("""^\s*(?:Caused by: |Suppressed: )?([^:\s]+)(?:: (.+))?$""")
private val FRAME_REGEX = Regex("""^\s*at ([^(]+)(\(.+\))?""")
