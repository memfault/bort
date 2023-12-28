package com.memfault.bort.http

import okhttp3.Interceptor

interface RetrofitInterceptor : Interceptor {

    val type: InterceptorType

    /**
     * The [InterceptorType] is used to sort the interceptors so they're ordered deterministically.
     *
     * Types with the higher order are applied first.
     *
     * In general, interceptors that apply transformations should be applied first and thus should have a lower
     * order than interceptors that are only logging information.
     */
    enum class InterceptorType(val order: Int) {
        PROJECT_KEY(0),
        DEBUG_INFO(1),
        GZIP(2),
        LOGGING(3),
    }
}
