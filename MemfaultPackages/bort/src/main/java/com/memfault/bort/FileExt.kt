package com.memfault.bort.fileExt

import java.io.File
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source

fun File.md5Hex(): String =
    HashingSource.md5(this.source()).use { hashingSource ->
        hashingSource.buffer().use { source ->
            source.readAll(blackholeSink())
            hashingSource.hash.hex()
        }
    }
