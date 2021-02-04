package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import org.junit.jupiter.api.Test

class AppComponentsTest {

    @Test
    fun alwaysEnabledProvider() {
        // Test that the stub provider isn't used when runtime enable is required
        val context: Context = mockk {
            every { cacheDir } returns File("/tmp")
            every { getSharedPreferences(any(), any()) } returns makeFakeSharedPreferences()
        }

        val getStringDefaultSlot = slot<String>()
        val sharedPreferences = mockk<SharedPreferences>()
        every {
            sharedPreferences.getString(any(), capture(getStringDefaultSlot))
        } answers { getStringDefaultSlot.captured }

        AppComponents.Builder(context, sharedPreferences).apply {
            settingsProvider = mockk {
                every { isRuntimeEnableRequired } returns true
                every { httpApiSettings } returns object : HttpApiSettings {
                    override val filesBaseUrl = "https://test.com"
                    override val ingressBaseUrl = "https://ingress.test.com"
                    override val uploadNetworkConstraint = NetworkConstraint.CONNECTED
                    override val uploadCompressionEnabled = true
                    override val projectKey = "SECRET"
                }
                every { sdkVersionInfo } returns mockk()
                every { deviceInfoSettings } returns mockk()
            }
            deviceIdProvider = mockk {
                every { deviceId() } returns "abc"
            }
            extraDropBoxEntryProcessors = emptyMap()
            reporterServiceConnector = mockk()
        }.build().also {
            assert(it.bortEnabledProvider !is BortAlwaysEnabledProvider)
        }
    }
}
