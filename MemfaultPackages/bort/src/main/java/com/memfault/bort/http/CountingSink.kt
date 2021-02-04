package com.memfault.bort.http

import okio.Buffer
import okio.Sink
import okio.Timeout

class CountingSink : Sink {
    var byteCount: Long = 0
        private set

    override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount).also {
        this.byteCount += byteCount
    }
    override fun flush() {}
    override fun timeout() = Timeout.NONE
    override fun close() {}
}
