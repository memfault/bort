package com.memfault.bort.http

import com.memfault.bort.http.RetrofitInterceptor.InterceptorType
import com.memfault.bort.http.RetrofitInterceptor.InterceptorType.LOGGING
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.REQUEST_ATTEMPT
import com.memfault.bort.metrics.REQUEST_FAILED
import com.memfault.bort.metrics.REQUEST_TIMING
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.time.measureTimedValue

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
class StandardLoggingNetworkInterceptor @Inject constructor(
    private val metrics: BuiltinMetricsStore,
) : RetrofitInterceptor by LoggingNetworkInterceptor(
    obfuscateSensitiveData = true,
    metrics = metrics,
)

class LoggingNetworkInterceptor(
    private val obfuscateSensitiveData: Boolean,
    private val metrics: BuiltinMetricsStore,
) : RetrofitInterceptor {

    private val requestAttemptCounter = Reporting.report()
        .counter(name = REQUEST_ATTEMPT, sumInReport = true, internal = true)

    private val requestTimingDistribution =
        Reporting.report().distribution(
            name = REQUEST_TIMING,
            aggregations = listOf(NumericAgg.COUNT, NumericAgg.MIN, NumericAgg.MAX, NumericAgg.SUM),
            internal = true,
        )

    private val memfaultSyncSuccessOrFailure = Reporting.report()
        .successOrFailure("sync_memfault")

    override val type: InterceptorType = LOGGING

    override fun intercept(chain: Interceptor.Chain): Response = logTimeout(metrics) {
        requestAttemptCounter.increment()

        val request = chain.request()
        val xRequestId = request.header(X_REQUEST_ID)
        val projectKey = if (obfuscateSensitiveData) {
            request.header(PROJECT_KEY_HEADER)?.let { obfuscateProjectKey(it) }
        } else {
            request.header(PROJECT_KEY_HEADER)
        }
        val requestUrl = if (obfuscateSensitiveData) scrubUrl(request.url) else request.url

        Logger.v("""Sending request {"url": "$requestUrl", "id": "$xRequestId", "key": "$projectKey"}""")
        if (!obfuscateSensitiveData) Logger.v("""Headers: ${request.headers}""")

        val timedValue = measureTimedValue {
            chain.proceed(request)
        }

        val response = timedValue.value
        val delta = timedValue.duration.inWholeMilliseconds

        requestTimingDistribution.record(delta)

        val responseUrl = if (obfuscateSensitiveData) scrubUrl(response.request.url) else response.request.url

        Logger.v("Received response for $responseUrl in ${decimalFormat.format(delta)} ms")
        if (!obfuscateSensitiveData) Logger.v("""Headers: ${response.headers}""")

        memfaultSyncSuccessOrFailure.record(response.isSuccessful)

        if (!response.isSuccessful) {
            val responseCode = response.code
            val responseMessage = response.message
            logFailure(responseCode, metrics)

            Logger.w("""Request failed {"code": $responseCode, "id": "$xRequestId", "message": "$responseMessage"}""")
        }
        response
    }

    private fun metricForFailure(tag: String) = "${REQUEST_FAILED}_$tag"

    private fun logFailure(
        code: Int,
        metrics: BuiltinMetricsStore,
    ) = metrics.increment(metricForFailure(code.toString()))

    private fun logTimeout(
        metrics: BuiltinMetricsStore,
        block: () -> Response,
    ): Response = try {
        block()
    } catch (e: Exception) {
        if (e is SocketTimeoutException) metrics.increment(metricForFailure("timeout_socket"))
        if (e is InterruptedIOException) metrics.increment(metricForFailure("timeout_call"))
        if (e is ConnectException) metrics.increment(metricForFailure("connect"))
        throw e
    }
}
