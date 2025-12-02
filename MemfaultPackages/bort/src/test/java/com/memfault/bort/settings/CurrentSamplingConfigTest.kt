package com.memfault.bort.settings

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
    private val fleetSamplingSettings = object : FleetSamplingSettings {
        override val loggingActive: Boolean = false
        override val debuggingActive: Boolean = false
        override val monitoringActive: Boolean = false
    }
    private val currentSamplingConfig = CurrentSamplingConfig(
        configPref = configPref,
        fleetSamplingSettings = fleetSamplingSettings,
    )

    @Test
    fun handlesConfigChange() = runTest {
        val originalConfig = SamplingConfig(revision = 1)
        storedConfig = originalConfig
        val newConfig = SamplingConfig(revision = 2)
        currentSamplingConfig.update(newConfig)
        verify(exactly = 1) { configPref.set(newConfig) }
    }

    @Test
    fun resendsRevision() = runTest {
        currentSamplingConfig.update(SamplingConfig(revision = 2))
        verify(exactly = 1) { configPref.set(any()) }
    }
}
