package com.memfault.bort.http

import com.memfault.bort.shared.Logger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response


private fun obfuscateProjectKey(projectKey: String) =
    "${projectKey.subSequence(0, 2)}...${projectKey.last()}"


class LoggingNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val xRequestId = request.header(X_REQUEST_ID)
        val obfuscatedProjectKey: String = request.header(PROJECT_KEY_HEADER)?.let {
            obfuscateProjectKey(it)
        } ?: ""
        val t1: Long = System.nanoTime()
        Logger.v(
            """
Sending request
> ${request.url()}
> ID=$xRequestId
> key=$obfuscatedProjectKey
""".trimEnd())
        val response: Response = chain.proceed(request)
        val t2: Long = System.nanoTime()
        val delta = (t2 - t1) / 1e6
        Logger.v(
            """
Received response for ${response.request().url()} in ${String.format("%.1f", delta)} ms
        """.trimEnd()
        )

        if (!response.isSuccessful) {
            Logger.w(
                """
Request failed
> code=${response.code()}
> ID=$xRequestId
> message=${response.message()}
""".trimEnd())
        }
        return response
    }
}
