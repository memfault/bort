package com.memfault.bort.parsers

import java.io.InputStream

data class Anr(val packageName: String?)

class AnrParser(val inputStream: InputStream) {
    fun parse(): Anr {
        val lines = Lines(inputStream.bufferedReader().lineSequence().asIterable())
        return Anr(
            ActivityManagerHeaderParser.dropUntilAndGetPackageName(lines),
        )
    }
}
