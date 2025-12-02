package com.memfault.bort.parsers

data class Anr(
    val packageName: String?,
)

class AnrParser(
    private val linesSequence: Sequence<String>,
) {
    fun parse(): Anr {
        var packageName: String? = null
        for (line in linesSequence) {
            packageName = AmMetadataHeader.parsePackage(line)
            if (packageName != null) {
                break
            }
        }

        return Anr(
            packageName = packageName,
        )
    }
}
