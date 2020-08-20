package com.memfault.bort.http

import com.memfault.bort.Logger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class LoggingNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val t1: Long = System.nanoTime()
        Logger.v("Sending request ${request.url()}")
        val response: Response = chain.proceed(request)
        val t2: Long = System.nanoTime()
        val delta = (t2 - t1) / 1e6
        Logger.v(
            """
Received response for ${response.request().url()} in ${String.format("%.1f", delta)} ms
        """.trimEnd()
        )
        return response
    }
}
