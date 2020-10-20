package com.memfault.bort

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoFromSettingsAndDumpster {
    val settingsProvider: SettingsProvider
        get() = mock<SettingsProvider> {
            on { androidBuildVersionKey() } doReturn "ro.build.version.incremental"
            on { androidHardwareVersionKey() } doReturn "ro.product.board"
            on { androidSerialNumberKey() } doReturn "ro.serialno"
        }

    @Test
    fun happyPath() {
        val props = mapOf(
                "ro.serialno" to "SERIAL",
                "ro.product.board" to "HARDWARE-XYZ",
                "ro.build.version.incremental" to "123"
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settingsProvider, props)
        assertEquals("SERIAL", deviceInfo.deviceSerial)
        assertEquals("HARDWARE-XYZ", deviceInfo.hardwareVersion)
        assertEquals("123", deviceInfo.softwareVersion)
    }

    @Test
    fun missingValues() {
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settingsProvider, mapOf())
        assertEquals("unknown", deviceInfo.deviceSerial)
        assertEquals("unknown", deviceInfo.hardwareVersion)
        assertEquals("unknown", deviceInfo.softwareVersion)
    }
}

class DeviceInfoLegacy {
    val settingsProvider: SettingsProvider
        get() = mock<SettingsProvider> {
            on { androidHardwareVersionKey() } doReturn ""
        }

    /**
     * When androidHardwareVersionKey is "" use the legacy hardware version scheme:
     */
    @Test
    fun legacyHardwareVersion() {
        val hardwareVersion = DeviceInfo.hardwareVersionFromSettingsAndSystemProperties(settingsProvider, mapOf(
            "ro.product.brand" to "brand",
            "ro.product.name" to "product",
            "ro.product.device" to "device"
        ))
        assertEquals("brand-product-device", hardwareVersion)
    }

    @Test
    fun happyPath() {
        assertEquals("brand-product-device", DeviceInfo.getLegacyHardwareVersion(mapOf(
            "ro.product.brand" to "brand",
            "ro.product.name" to "product",
            "ro.product.device" to "device"
        )))
    }

    @Test
    fun missingValues() {
        assertEquals("device", DeviceInfo.getLegacyHardwareVersion(mapOf(
            "ro.product.device" to "device"
        )))
    }
}
