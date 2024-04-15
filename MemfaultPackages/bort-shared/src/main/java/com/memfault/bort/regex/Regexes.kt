package com.memfault.bort.regex

/**
 * Converts a string containing a star-glob to a Regex. All string elements are escaped in \Q\E, except for
 * asterisks which get converted to a Regex match-any (.*).
 */
fun String.toGlobRegex(): Regex =
    this.split("*")
        .joinToString(
            prefix = "\\Q",
            separator = "\\E.*\\Q",
            postfix = "\\E",
        ).toRegex()
