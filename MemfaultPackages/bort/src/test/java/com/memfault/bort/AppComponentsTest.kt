package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.settings.FileUploadHoldingAreaSettings
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.NetworkConstraint
import com.memfault.bort.shared.LogcatFilterSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlin.time.Duration
import kotlin.time.minutes
import kotlin.time.seconds
import org.junit.jupiter.api.Test

class AppComponentsTest {

    @Test
    fun alwaysEnabledProvider() {
        // Test that the stub provider isn't used when runtime enable is required
        val context: Context = mockk {
            every { cacheDir } returns File("/tmp")
            every { getSharedPreferences(any(), any()) } returns makeFakeSharedPreferences()
            every { packageName } returns "com.memfault.bort"
            every { resources } returns mockk(relaxed = true)
        }

        val getStringDefaultSlot = slot<String>()
        val sharedPreferences = mockk<SharedPreferences>()
        every {
            sharedPreferences.getString(any(), capture(getStringDefaultSlot))
        } answers { getStringDefaultSlot.captured }

        AppComponents.Builder(context, context.resources, sharedPreferences).apply {
            settingsProvider = mockk {
                every { bugReportSettings } returns mockk()
                every { isRuntimeEnableRequired } returns true
                every { httpApiSettings } returns object : HttpApiSettings {
                    override val filesBaseUrl = "https://test.com"
                    override val deviceBaseUrl = "https://device.test.com"
                    override val ingressBaseUrl = "https://ingress.test.com"
                    override val uploadNetworkConstraint = NetworkConstraint.CONNECTED
                    override val uploadCompressionEnabled = true
                    override val projectKey = "SECRET"
                    override val connectTimeout = 0.seconds
                    override val writeTimeout = 0.seconds
                    override val readTimeout = 0.seconds
                    override val callTimeout = 0.seconds
                }
                every { sdkVersionInfo } returns mockk()
                every { deviceInfoSettings } returns mockk()
                every { dropBoxSettings } returns mockk()
                every { logcatSettings } returns object : LogcatSettings {
                    override val dataSourceEnabled = true
                    override val collectionInterval = 15.minutes
                    override val commandTimeout: Duration = 1.minutes
                    override val filterSpecs: List<LogcatFilterSpec> = emptyList()
                }
                every { fileUploadHoldingAreaSettings } returns object : FileUploadHoldingAreaSettings {
                    override val trailingMargin = 5.minutes
                    override val maxStoredEventsOfInterest = 10
                }
                every { metricsSettings } returns mockk()
                every { packageManagerSettings } returns mockk()
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
