package com.memfault.bort.http

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.Sink
import okio.buffer
import retrofit2.Invocation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class GzipRequest

class GzipRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(transformRequest(chain.request()))
    }

    fun transformRequest(originalRequest: Request): Request {
        val annotation = originalRequest.tag(Invocation::class.java)?.method()?.getAnnotation(
            GzipRequest::class.java
        )
        val body = originalRequest.body
        if (annotation == null ||
            body == null ||
            originalRequest.header(CONTENT_ENCODING) != null
        ) {
            return originalRequest
        }
        return originalRequest.newBuilder()
            .header(CONTENT_ENCODING, "gzip")
            .method(originalRequest.method, body.gzip())
            .build()
    }
}

fun RequestBody.gzip(): RequestBody =
    object : RequestBody() {
        override fun contentType() = this@gzip.contentType()
        override fun contentLength() = gzipLength
        override fun writeTo(sink: BufferedSink) = gzipTo(sink)

        private val gzipLength: Long by lazy {
            CountingSink().apply {
                gzipTo(this)
            }.byteCount
        }

        private fun gzipTo(sink: Sink) {
            val gzipSink = GzipSink(sink).buffer()
            this@gzip.writeTo(gzipSink)
            gzipSink.close()
        }
    }

private const val CONTENT_ENCODING = "Content-Encoding"
