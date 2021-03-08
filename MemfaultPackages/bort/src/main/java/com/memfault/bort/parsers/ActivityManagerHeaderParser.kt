package com.memfault.bort.parsers

import com.memfault.bort.shared.consume

object ActivityManagerHeaderParser {
    fun dropActivityManagerMetadata(lines: Lines) {
        lines.until(consumeMatch = true) { it.isEmpty() }.use { it.consume() }
    }

    fun dropUntilAndGetPackageName(lines: Lines): String? {
        lines.until(consumeMatch = false) { it.startsWith("Package: ") }.use { it.consume() }
        return lines.peek()?.let {
            PACKAGE_REGEX.matchEntire(it)?.groupValues?.get(1)
        }
    }
}

private val PACKAGE_REGEX = "^Package: ([^ ]+).*".toRegex()
