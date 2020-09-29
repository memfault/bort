package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
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
                on { filesBaseUrl() } doReturn "https://test.com"
                on { ingressBaseUrl() } doReturn "https://ingress.test.com"
                on { bugReportNetworkConstraint() } doReturn NetworkConstraint.CONNECTED
                on { projectKey() } doReturn "SECRET"
            }
            deviceIdProvider = mock<DeviceIdProvider>() {
                on { deviceId() } doReturn "abc"
            }
        }.build().also {
            assert(it.bortEnabledProvider !is BortAlwaysEnabledProvider)
        }

    }
}
