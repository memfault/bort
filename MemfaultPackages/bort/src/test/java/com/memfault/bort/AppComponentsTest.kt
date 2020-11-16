package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.io.File
import org.junit.jupiter.api.Test

class AppComponentsTest {

    @Test
    fun alwaysEnabledProvider() {
        // Test that the stub provider isn't used when runtime enable is required
        val context: Context = mock {
            on { cacheDir } doReturn File("/tmp")
        }
        val sharedPreferences = mock<SharedPreferences>()
        AppComponents.Builder(context, sharedPreferences).apply {
            settingsProvider = mock {
                on { isRuntimeEnableRequired } doReturn true
                on { httpApiSettings } doReturn object : HttpApiSettings {
                    override val filesBaseUrl = "https://test.com"
                    override val ingressBaseUrl = "https://ingress.test.com"
                    override val uploadNetworkConstraint = NetworkConstraint.CONNECTED
                    override val projectKey = "SECRET"
                }
                on { sdkVersionInfo } doReturn mock()
                on { deviceInfoSettings } doReturn mock()
            }
            deviceIdProvider = mock {
                on { deviceId() } doReturn "abc"
            }
            extraDropBoxEntryProcessors = emptyMap()
            reporterServiceConnector = mock()
        }.build().also {
            assert(it.bortEnabledProvider !is BortAlwaysEnabledProvider)
        }
    }
}
