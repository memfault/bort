package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppComponentsTest {

    @Test
    fun alwaysEnabledProvider() {
        // Test that the stub provider isn't used when runtime enable is required
        val context = mock<Context>()
        val sharedPreferences = mock<SharedPreferences>()
        AppComponents.Builder(context, sharedPreferences).apply {
            settingsProvider = mock<SettingsProvider> {
                on { isRuntimeEnableRequired() } doReturn true
                on { baseUrl() } doReturn "https://test.com"
            }
            deviceIdProvider = mock<DeviceIdProvider>() {
                on { deviceId() } doReturn "abc"
            }
        }.build().also {
            assert(it.bortEnabledProvider !is BortAlwaysEnabledProvider)
        }

    }

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
                .build().also {request ->
                    DebugInfoInjectingInterceptor(
                        mock<SettingsProvider>(),
                        mock<DeviceIdProvider>()
                    ).transformRequest(request).let {
                        assertEquals(url, it.url().toString())
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
                .build().also {request ->
                    DebugInfoInjectingInterceptor(
                        mock<SettingsProvider>(),
                        mock<DeviceIdProvider>()
                    ).transformRequest(request).let { result ->
                        result.url().queryParameterNames().also { queryParamNames ->
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_UPSTREAM_GIT_SHA))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_NAME))
                            assert(queryParamNames.contains(QUERY_PARAM_VERSION_CODE))
                            assert(queryParamNames.contains(QUERY_PARAM_DEVICE_ID))
                        }
                        assertNotNull(result.header(X_REQUEST_ID))
                    }
                }
        }
    }
}
