package com.memfault.bort.parsers

object AmMetadataHeader {

    fun parsePackage(line: String): String? =
        PACKAGE_REGEX.matchEntire(line)?.groupValues?.get(1)

    fun notMetadataLine(s: String): Boolean = s.isBlank() ||
        !s.contains(": ") ||
        s.contains("Exception: ") ||
        s.contains("Error: ") ||
        s.contains("Failure: ")
}

private val PACKAGE_REGEX = "^Package: ([^ ]+).*".toRegex()
