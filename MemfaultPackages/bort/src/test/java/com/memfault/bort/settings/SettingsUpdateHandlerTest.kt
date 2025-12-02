package com.memfault.bort.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SettingsUpdateHandlerTest {
    private val structuredLogSettingsMock = object : StructuredLogSettings {
        override val dataSourceEnabled = true
        override val rateLimitingSettings = RateLimitingSettings(0, 0.seconds.boxed(), 0)
        override val dumpPeriod: Duration = 0.seconds
        override val numEventsBeforeDump: Long = 0
        override val maxMessageSizeBytes: Long = 0
        override val minStorageThresholdBytes: Long = 0
        override val metricsReportEnabled: Boolean = true
        override val highResMetricsEnabled: Boolean = true
    }
    private val settingsProvider: DynamicSettingsProvider = mockk {
        every { invalidate() } returns Unit
        every { structuredLogSettings } returns structuredLogSettingsMock
    }
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider = mockk {
        every { set(any()) } returns Unit
        every { get() } returns SETTINGS_FIXTURE.toSettings()
    }
    private val fetchedSettingsUpdateSlot = slot<FetchedSettingsUpdate>()
    private val callback: SettingsUpdateCallback = mockk {
        coEvery { onSettingsUpdated(any(), capture(fetchedSettingsUpdateSlot)) } returns Unit
    }
    private val everFetchedSettingsPreferenceProvider =
        EverFetchedSettingsPreferenceProvider(makeFakeSharedPreferences())
    private val currentSamplingConfig: CurrentSamplingConfig = mockk(relaxed = true)
    private val marHoldingArea: MarFileHoldingArea = mockk(relaxed = true)

    private val handler = SettingsUpdateHandler(
        settingsProvider = settingsProvider,
        storedSettingsPreferenceProvider = storedSettingsPreferenceProvider,
        settingsUpdateCallback = callback,
        metrics = mockk(relaxed = true),
        everFetchedSettingsPreferenceProvider = everFetchedSettingsPreferenceProvider,
        currentSamplingConfig = currentSamplingConfig,
        marFileHoldingArea = marHoldingArea,
    )

    @Test
    fun validResponse() = runTest {
        val response1 = SETTINGS_FIXTURE.toSettings()
        val deviceConfigResponse1 = DecodedDeviceConfig(
            revision = 1,
            completedRevision = 1,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = response1,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(),
            ),
            others = JsonObject(emptyMap()),
        )
        handler.handleDeviceConfig(deviceConfigResponse1)

        // The first call returns the same stored fixture and thus set() won't be called
        verify {
            storedSettingsPreferenceProvider.get()
        }
        confirmVerified(storedSettingsPreferenceProvider)

        coVerify(exactly = 1) {
            currentSamplingConfig.update(
                SamplingConfig(
                    revision = 1,
                    debuggingResolution = SamplingConfig.DEFAULT_DEBUGGING,
                    loggingResolution = SamplingConfig.DEFAULT_LOGGING,
                    monitoringResolution = SamplingConfig.DEFAULT_MONITORING,
                ),
            )
        }
        coVerify(exactly = 1) { marHoldingArea.addDeviceConfigMarEntry(1) }
        coVerify(exactly = 1) {
            marHoldingArea.handleSamplingConfigChange(
                SamplingConfig(
                    revision = 1,
                    debuggingResolution = SamplingConfig.DEFAULT_DEBUGGING,
                    loggingResolution = SamplingConfig.DEFAULT_LOGGING,
                    monitoringResolution = SamplingConfig.DEFAULT_MONITORING,
                ),
                null,
            )
        }

        // The second one will trigger the update
        val response2 = response1.copy(bortMinLogcatLevel = LogLevel.NONE.level)
        val deviceConfigResponse2 = DecodedDeviceConfig(
            revision = 2,
            completedRevision = 2,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = response2,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(
                    debuggingResolution = "low",
                    loggingResolution = "low",
                    monitoringResolution = "low",
                ),
            ),
            others = JsonObject(emptyMap()),
        )
        handler.handleDeviceConfig(deviceConfigResponse2)

        // Check that settings was invalidated after a remote update
        coVerify {
            storedSettingsPreferenceProvider.get()
            storedSettingsPreferenceProvider.set(response2)
            settingsProvider.invalidate()
            callback.onSettingsUpdated(any(), any())
            settingsProvider.structuredLogSettings
            settingsProvider.structuredLogSettings
        }
        confirmVerified(settingsProvider)
        assertThat(fetchedSettingsUpdateSlot.captured.old).isEqualTo(SETTINGS_FIXTURE.toSettings())
        assertThat(fetchedSettingsUpdateSlot.captured.new).isEqualTo(response2)

        coVerify {
            currentSamplingConfig.update(
                SamplingConfig(
                    revision = 2,
                    debuggingResolution = Resolution.LOW,
                    loggingResolution = Resolution.LOW,
                    monitoringResolution = Resolution.LOW,
                ),
            )
        }
        coVerify(exactly = 1) { marHoldingArea.addDeviceConfigMarEntry(2) }
        coVerify(exactly = 1) {
            marHoldingArea.handleSamplingConfigChange(
                SamplingConfig(
                    revision = 2,
                    debuggingResolution = Resolution.LOW,
                    loggingResolution = Resolution.LOW,
                    monitoringResolution = Resolution.LOW,
                ),
                null,
            )
        }
    }

    @Test
    fun skipDeviceConfigExactSame() = runTest {
        val defaultSamplingConfig = SamplingConfig(
            revision = 1,
            debuggingResolution = SamplingConfig.DEFAULT_DEBUGGING,
            loggingResolution = SamplingConfig.DEFAULT_LOGGING,
            monitoringResolution = SamplingConfig.DEFAULT_MONITORING,
        )
        coEvery { currentSamplingConfig.get() } answers { defaultSamplingConfig }
        val response1 = SETTINGS_FIXTURE.toSettings()
        val deviceConfigResponse1 = DecodedDeviceConfig(
            revision = 1,
            completedRevision = 1,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = response1,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(),
            ),
            others = JsonObject(emptyMap()),
        )
        handler.handleDeviceConfig(deviceConfigResponse1)

        coVerify(exactly = 1) { currentSamplingConfig.update(defaultSamplingConfig) }
        coVerify(exactly = 0) { marHoldingArea.addDeviceConfigMarEntry(any()) }
        coVerify(exactly = 0) { marHoldingArea.handleSamplingConfigChange(any(), any()) }
    }

    @Test
    fun validSampling() = runTest {
        val response1 = SETTINGS_FIXTURE.toSettings()
        val deviceConfigResponse1 = DecodedDeviceConfig(
            revision = 1,
            completedRevision = 1,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(sdkSettings = response1),
                sampling = FetchedDeviceConfigContainer.Sampling(),
            ),
            others = JsonObject(emptyMap()),
        )
        handler.handleDeviceConfig(deviceConfigResponse1)

        // The first call returns the same stored fixture and thus set() won't be called

        val config = SamplingConfig(
            revision = 1,
            debuggingResolution = SamplingConfig.DEFAULT_DEBUGGING,
            loggingResolution = SamplingConfig.DEFAULT_LOGGING,
            monitoringResolution = SamplingConfig.DEFAULT_MONITORING,
        )
        coVerify { currentSamplingConfig.update(config) }
        coEvery { currentSamplingConfig.get() } answers { config }

        // The second one will trigger the update
        val response2 = response1.copy(bortMinLogcatLevel = LogLevel.NONE.level)
        val deviceConfigResponse2 = DecodedDeviceConfig(
            revision = 2,
            completedRevision = 2,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = response2,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(
                    debuggingResolution = "low",
                    loggingResolution = "low",
                    monitoringResolution = "low",
                ),
            ),
            others = JsonObject(emptyMap()),
        )
        handler.handleDeviceConfig(deviceConfigResponse2)

        coVerify {
            currentSamplingConfig.update(
                SamplingConfig(
                    revision = 2,
                    debuggingResolution = Resolution.LOW,
                    loggingResolution = Resolution.LOW,
                    monitoringResolution = Resolution.LOW,
                ),
            )
        }
        coVerify(exactly = 1) { marHoldingArea.addDeviceConfigMarEntry(2) }
        coVerify(exactly = 1) {
            marHoldingArea.handleSamplingConfigChange(
                SamplingConfig(
                    revision = 2,
                    debuggingResolution = Resolution.LOW,
                    loggingResolution = Resolution.LOW,
                    monitoringResolution = Resolution.LOW,
                ),
                null,
            )
        }
    }
}
