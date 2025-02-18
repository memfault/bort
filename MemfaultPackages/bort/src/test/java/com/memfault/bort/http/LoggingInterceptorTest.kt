package com.memfault.bort.http

import assertk.assertThat
import assertk.assertions.isEqualTo
import okhttp3.HttpUrl
import org.junit.Test

class LoggingInterceptorTest {
    @Test
    fun scrubUrl() {
        val originalUrl = HttpUrl.Builder().apply {
            scheme("https")
            host("memfault.com")
            addQueryParameter(QUERY_PARAM_DEVICE_SERIAL, "pii-sn")
        }.build()
        assertThat(scrubUrl(originalUrl).toString()).isEqualTo("https://memfault.com/?deviceSerial=***SCRUBBED***")
    }
}
