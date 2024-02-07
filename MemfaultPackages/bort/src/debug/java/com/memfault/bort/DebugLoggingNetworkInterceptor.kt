package com.memfault.bort

import com.memfault.bort.http.LoggingNetworkInterceptor
import com.memfault.bort.http.RetrofitInterceptor
import com.memfault.bort.http.StandardLoggingNetworkInterceptor
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class, replaces = [StandardLoggingNetworkInterceptor::class])
class DebugLoggingNetworkInterceptor @Inject constructor(
    private val metrics: BuiltinMetricsStore,
) : RetrofitInterceptor by LoggingNetworkInterceptor(
    // No need to obfuscate data in debug mode. Helps with debugging.
    obfuscateSensitiveData = false,
    metrics = metrics,
)
