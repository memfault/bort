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
                on { baseUrl() } doReturn "https://test.com"
            }
        }.build().also {
            assert(it.bortEnabledProvider !is BortAlwaysEnabledProvider)
        }

    }
}
