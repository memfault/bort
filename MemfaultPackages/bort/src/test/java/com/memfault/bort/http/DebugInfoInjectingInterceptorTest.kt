package com.memfault.bort.http

import com.memfault.bort.DeviceIdProvider
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Request
import org.junit.Assert
import org.junit.jupiter.api.Test

class DebugInfoInjectingInterceptorTest {
    @Test
    fun transformRequestRespectsWhitelist() {
        listOf(
            "https://memfault.otherdomain.org/",
            "https://foo.com/",
            "http://foo.com/",
            "http://127.0.0.1:5000/",
            "http://localhost:5000/"
        ).forEach { url ->
            Request.Builder()
                .url(url)
                .build().also { request ->
                    DebugInfoInjectingInterceptor(
                        mock(),
                        mock<DeviceIdProvider>()
                    ).transformRequest(request).let {
                        Assert.assertEquals(url, it.url.toString())
                    }
                }
        }
    }

    @Test
    fun transformMemfaultOrCleartextApiRequests() {
        listOf(
            "https://memfault.com/",
            "https://sub.memfault.com/",
            "http://localhost:9000/api",
            "http://127.0.0.1:5000/api"
        ).forEach { url ->
            Request.Builder()
                .url(url)
                .build().also { request ->
                    DebugInfoInjectingInterceptor(
                        mock {
                            on { upstreamVersionName } doReturn "version"
                            on { upstreamVersionCode } doReturn 1
                            on { upstreamGitSha } doReturn "sha"
                            on { appVersionName } doReturn "appVersion"
                            on { appVersionCode } doReturn 1
                        },
                        mock<DeviceIdProvider>() {
                            on { deviceId() } doReturn "DEMO"
                        }
                    ).transformRequest(request).let { result ->
                        result.url.queryParameterNames.also { queryParamNames ->
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_GIT_SHA))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_DEVICE_ID))
                        }
                        Assert.assertNotNull(result.header(X_REQUEST_ID))
                    }
                }
        }
    }
}
