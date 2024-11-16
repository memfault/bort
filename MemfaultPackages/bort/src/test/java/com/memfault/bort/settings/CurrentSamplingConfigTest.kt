package com.memfault.bort.settings

import com.memfault.bort.clientserver.MarFileHoldingArea
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class CurrentSamplingConfigTest {
    private var storedConfig = SamplingConfig()
    private val configPref: SamplingConfigPreferenceProvider = mockk(relaxed = true) {
        every { get() } answers { storedConfig }
    }
    private val marHoldingArea: MarFileHoldingArea = mockk(relaxed = true)
    private val fleetSamplingSettings = object : FleetSamplingSettings {
        override val loggingActive: Boolean = false
        override val debuggingActive: Boolean = false
        override val monitoringActive: Boolean = false
    }
    private val currentSamplingConfig = CurrentSamplingConfig(
        configPref = configPref,
        marFileHoldingArea = { marHoldingArea },
        fleetSamplingSettings = fleetSamplingSettings,
    )

    @Test
    fun handlesConfigChange() {
        runTest {
            val originalConfig = SamplingConfig(revision = 1)
            storedConfig = originalConfig
            val newConfig = SamplingConfig(revision = 2)
            currentSamplingConfig.update(newConfig, completedRevision = 1)
            verify(exactly = 1) { configPref.set(newConfig) }
            coVerify(exactly = 0) { marHoldingArea.addDeviceConfigMarEntry(any()) }
            coVerify(exactly = 1) { marHoldingArea.handleSamplingConfigChange(newConfig) }
        }
    }

    @Test
    fun resendsRevision() {
        runTest {
            val originalConfig = SamplingConfig(revision = 1)
            storedConfig = originalConfig
            currentSamplingConfig.update(originalConfig, completedRevision = 0)
            verify(exactly = 0) { configPref.set(any()) }
            coVerify(exactly = 1) { marHoldingArea.addDeviceConfigMarEntry(1) }
            coVerify(exactly = 0) { marHoldingArea.handleSamplingConfigChange(any()) }
        }
    }

    @Test
    fun doesNothing() {
        runTest {
            val originalConfig = SamplingConfig(revision = 1)
            storedConfig = originalConfig
            currentSamplingConfig.update(originalConfig, completedRevision = 1)
            verify(exactly = 0) { configPref.set(any()) }
            coVerify(exactly = 0) { marHoldingArea.addDeviceConfigMarEntry(any()) }
            coVerify(exactly = 0) { marHoldingArea.handleSamplingConfigChange(any()) }
        }
    }
}
