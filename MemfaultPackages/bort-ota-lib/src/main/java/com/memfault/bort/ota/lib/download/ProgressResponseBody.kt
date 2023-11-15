package com.memfault.bort.ota.lib.download

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

typealias ProgressCallback = (bytesRead: Long, contentLength: Long) -> Unit

/**
 * A wrapper around OkHttp's ResponseBody that takes the response body and a listener callback and issues progress
 * updates as the body is read.
 */
class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressCallback: ProgressCallback,
    private val initialBytesRead: Long,
) : ResponseBody() {
    private lateinit var bufferedSource: BufferedSource

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun source(): BufferedSource =
        if (this::bufferedSource.isInitialized) {
            bufferedSource
        } else {
            sourceWithProgress(responseBody.source()).buffer().also { this.bufferedSource = it }
        }

    private fun sourceWithProgress(source: BufferedSource): Source =
        object : ForwardingSource(source) {
            private var totalBytesRead: Long = initialBytesRead

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                progressCallback(totalBytesRead, responseBody.contentLength())
                return bytesRead
            }
        }
}

/**
 * Converts a ResponseBody to a {@see #ProgressResponseBody}
 */
fun ResponseBody.withProgressCallback(callback: ProgressCallback, initialBytesRead: Long = 0) =
    ProgressResponseBody(this, callback, initialBytesRead)
