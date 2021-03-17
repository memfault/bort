package com.memfault.bort.http

import com.memfault.bort.shared.Logger
import java.text.DecimalFormat
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

private fun obfuscateProjectKey(projectKey: String) =
    "${projectKey.subSequence(0, 2)}...${projectKey.last()}"

internal fun scrubUrl(url: HttpUrl): HttpUrl =
    url.newBuilder().apply {
        setQueryParameter(QUERY_PARAM_DEVICE_SERIAL, "***SCRUBBED***")
    }.build()

// Not using String.format() to avoid bug in AOSP 8.x java.util.Formatter implementation.
// See https://github.com/facebook/fresco/issues/2504#issuecomment-657771489
private val decimalFormat = DecimalFormat("#.##")

class LoggingNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val xRequestId = request.header(X_REQUEST_ID)
        val obfuscatedProjectKey: String = request.header(PROJECT_KEY_HEADER)?.let {
            obfuscateProjectKey(it)
        } ?: ""
        val t1: Long = System.nanoTime()
        val scrubbedUrl = scrubUrl(request.url)
        Logger.v(
            """
Sending request
> $scrubbedUrl
> ID=$xRequestId
> key=$obfuscatedProjectKey
""".trimEnd()
        )
        val response: Response = chain.proceed(request)
        val t2: Long = System.nanoTime()
        val delta = (t2 - t1) / 1e6
        Logger.v(
            """
Received response for $scrubbedUrl in ${decimalFormat.format(delta)} ms
        """.trimEnd()
        )

        if (!response.isSuccessful) {
            Logger.w(
                """
Request failed
> code=${response.code}
> ID=$xRequestId
> message=${response.message}
""".trimEnd()
            )
        }
        return response
    }
}
