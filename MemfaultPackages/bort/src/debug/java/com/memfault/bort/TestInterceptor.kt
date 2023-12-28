package com.memfault.bort

import com.memfault.bort.http.LoggingNetworkInterceptor
import com.memfault.bort.http.RetrofitInterceptor
import com.memfault.bort.http.RetrofitInterceptor.InterceptorType
import com.memfault.bort.http.RetrofitInterceptor.InterceptorType.LOGGING
import com.memfault.bort.http.logAttempt
import com.memfault.bort.http.logFailure
import com.memfault.bort.http.logTimeout
import com.memfault.bort.http.logTimings
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class, replaces = [LoggingNetworkInterceptor::class])
class TestInterceptor @Inject constructor(
    private val metrics: BuiltinMetricsStore,
) : RetrofitInterceptor {

    override val type: InterceptorType = LOGGING

    override fun intercept(chain: Interceptor.Chain): Response = logTimeout(metrics) {
        logAttempt(metrics)
        val request: Request = chain.request()
        val t1: Long = System.nanoTime()
        Logger.v("Sending request ${request.url} on ${chain.connection()}, headers: ${request.headers}")
        val response: Response = chain.proceed(request)
        val t2: Long = System.nanoTime()
        val delta = (t2 - t1) / 1e6
        logTimings(delta.toLong(), metrics)
        Logger.v(
            """Received response for ${response.request.url} in ${String.format("%.1f", delta)} ms
                   ${response.headers}
            """.trimIndent(),
        )
        if (!response.isSuccessful) {
            logFailure(response.code, metrics)
            Logger.w("Request failed! code=${response.code}, message=${response.message}")
        }

        response
    }
}
