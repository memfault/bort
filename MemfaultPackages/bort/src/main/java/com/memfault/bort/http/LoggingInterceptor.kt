package com.memfault.bort.http

import com.memfault.bort.http.RetrofitInterceptor.InterceptorType
import com.memfault.bort.http.RetrofitInterceptor.InterceptorType.LOGGING
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.REQUEST_ATTEMPT
import com.memfault.bort.metrics.REQUEST_FAILED
import com.memfault.bort.metrics.REQUEST_TIMING
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.DecimalFormat
import javax.inject.Inject

private fun obfuscateProjectKey(projectKey: String) =
    "${projectKey.subSequence(0, 2)}...${projectKey.last()}"

internal fun scrubUrl(url: HttpUrl): HttpUrl =
    url.newBuilder().apply {
        setQueryParameter(QUERY_PARAM_DEVICE_SERIAL, "***SCRUBBED***")
    }.build()

// Not using String.format() to avoid bug in AOSP 8.x java.util.Formatter implementation.
// See https://github.com/facebook/fresco/issues/2504#issuecomment-657771489
private val decimalFormat = DecimalFormat("#.##")

@ContributesMultibinding(SingletonComponent::class)
class LoggingNetworkInterceptor @Inject constructor(
    private val metrics: BuiltinMetricsStore,
) : RetrofitInterceptor {

    override val type: InterceptorType = LOGGING

    override fun intercept(chain: Interceptor.Chain): Response = logTimeout(metrics) {
        logAttempt(metrics)
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
            """.trimEnd(),
        )
        val response: Response = chain.proceed(request)
        val t2: Long = System.nanoTime()
        val delta = (t2 - t1) / 1e6
        logTimings(delta.toLong(), metrics)
        Logger.v(
            """
Received response for $scrubbedUrl in ${decimalFormat.format(delta)} ms
            """.trimEnd(),
        )

        if (!response.isSuccessful) {
            logFailure(response.code, metrics)
            Logger.w(
                """
Request failed
> code=${response.code}
> ID=$xRequestId
> message=${response.message}
                """.trimEnd(),
            )
        }
        response
    }
}

fun logAttempt(metrics: BuiltinMetricsStore) = metrics.increment(REQUEST_ATTEMPT)

fun logTimings(timeMs: Long, metrics: BuiltinMetricsStore) = metrics.addValue(REQUEST_TIMING, timeMs)

private fun metricforFailure(tag: String) = "${REQUEST_FAILED}_$tag"

fun logFailure(code: Int, metrics: BuiltinMetricsStore) = metrics.increment(metricforFailure(code.toString()))

fun logTimeout(metrics: BuiltinMetricsStore, block: () -> Response): Response = try {
    block()
} catch (e: Exception) {
    if (e is SocketTimeoutException) metrics.increment(metricforFailure("timeout_socket"))
    if (e is InterruptedIOException) metrics.increment(metricforFailure("timeout_call"))
    if (e is ConnectException) metrics.increment(metricforFailure("connect"))
    throw e
}
