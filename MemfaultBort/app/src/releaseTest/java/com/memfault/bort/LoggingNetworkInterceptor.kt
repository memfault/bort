package com.memfault.bort

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class LoggingNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val t1: Long = System.nanoTime()
        Logger.v("Sending request ${request.url()} on ${chain.connection()} ${request.headers()}")
        val response: Response = chain.proceed(request)
        val t2: Long = System.nanoTime()
        Logger.v(
            """
Received response for ${response.request().url()} in ${String.format("%.1f", (t2 - t1) / 1e6)} ms
${response.headers()}
            """
        )
        return response
    }
}
