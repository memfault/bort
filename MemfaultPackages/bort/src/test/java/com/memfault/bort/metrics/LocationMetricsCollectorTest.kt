package com.memfault.bort.metrics

import com.memfault.bort.android.FakeDeviceFeatures
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.settings.LocationSettings
import com.memfault.bort.settings.SettingsFlow
import com.memfault.bort.settings.SettingsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class LocationMetricsCollectorTest {
    private val collectLocationDumpsys: CollectLocationDumpsys = mockk()
    private val gnssMetricsStorage: GnssMetricsStorage = mockk()
    private val readBootId: LinuxBootId = LinuxBootId { "bootid" }
    private val settingsFlow = object : SettingsFlow {
        override val settings: Flow<SettingsProvider> = emptyFlow()
    }

    private fun collector(deviceFeatures: FakeDeviceFeatures, dataSourceEnabled: Boolean = true) =
        LocationMetricsCollector(
            collectLocationDumpsys = collectLocationDumpsys,
            locationSettings = object : LocationSettings {
                override val dataSourceEnabled = dataSourceEnabled
                override val commandTimeout = 1.minutes
            },
            settingsFlow = settingsFlow,
            gnssMetricsStorage = gnssMetricsStorage,
            readBootId = readBootId,
            deviceFeatures = deviceFeatures,
        )

    @Test
    fun noGpsSkipsCollection() = runTest {
        collector(FakeDeviceFeatures(hasGps = false)).collect()
        coVerify(exactly = 0) { collectLocationDumpsys() }
    }

    @Test
    fun hasGpsCollects() = runTest {
        coEvery { collectLocationDumpsys() } returns null
        collector(FakeDeviceFeatures(hasGps = true)).collect()
        coVerify(exactly = 1) { collectLocationDumpsys() }
    }
}
