package com.memfault.bort

import com.memfault.bort.settings.AndroidBuildFormat
import com.memfault.bort.settings.DeviceInfoSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

val TEST_BUILD_FINGERPRINT = "Sony/H8314/H8314:9/52.0.A.3.163/772046555:user/release-keys"
val fakeGetBuildFingerprint = { TEST_BUILD_FINGERPRINT }

class DeviceInfoFromSettingsAndProperties {
    lateinit var settings: DeviceInfoSettings

    @BeforeEach
    fun setUp() {
        settings = mockk {
            every { androidBuildFormat } returns AndroidBuildFormat.SYSTEM_PROPERTY_ONLY
            every { androidBuildVersionKey } returns "ro.build.version.incremental"
            every { androidHardwareVersionKey } returns "ro.product.board"
            every { androidSerialNumberKey } returns "ro.serialno"
        }
    }

    @Test
    fun happyPathHardwareAndDeviceSerial() {
        val props = mapOf(
            "ro.serialno" to "SERIAL",
            "ro.product.board" to "HARDWARE-XYZ",
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settings, props)
        assertEquals("SERIAL", deviceInfo.deviceSerial)
        assertEquals("HARDWARE-XYZ", deviceInfo.hardwareVersion)
    }

    @Test
    fun happyPathSoftwareVersionSystemPropertyOnly() {
        every { settings.androidBuildFormat } returns AndroidBuildFormat.SYSTEM_PROPERTY_ONLY
        val props = mapOf(
            "ro.build.version.incremental" to "123"
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settings, props)
        assertEquals("123", deviceInfo.softwareVersion)
    }

    @Test
    fun happyPathSoftwareVersionBuildFingerPrintOnly() {
        every { settings.androidBuildFormat } returns AndroidBuildFormat.BUILD_FINGERPRINT_ONLY
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings, mapOf(), getBuildFingerprint = fakeGetBuildFingerprint
        )
        assertEquals(TEST_BUILD_FINGERPRINT, deviceInfo.softwareVersion)
    }

    @Test
    fun happyPathSoftwareVersionBuildFingerPrintAndSystemProperty() {
        every { settings.androidBuildFormat } returns AndroidBuildFormat.BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY
        val props = mapOf(
            "ro.build.version.incremental" to "123"
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings, props, getBuildFingerprint = fakeGetBuildFingerprint
        )
        assertEquals(
            "$TEST_BUILD_FINGERPRINT::123",
            deviceInfo.softwareVersion
        )
    }

    @Test
    fun missingValues() {
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settings, mapOf())
        assertEquals("unknown", deviceInfo.deviceSerial)
        assertEquals("unknown", deviceInfo.hardwareVersion)
        assertEquals("unknown", deviceInfo.softwareVersion)
    }
}

class DeviceInfoLegacy {
    val settings: DeviceInfoSettings
        get() = mockk {
            every { androidHardwareVersionKey } returns ""
        }

    /**
     * When androidHardwareVersionKey is "" use the legacy hardware version scheme:
     */
    @Test
    fun legacyHardwareVersion() {
        val hardwareVersion = DeviceInfo.hardwareVersionFromSettingsAndSystemProperties(
            settings,
            mapOf(
                "ro.product.brand" to "brand",
                "ro.product.name" to "product",
                "ro.product.device" to "device"
            )
        )
        assertEquals("brand-product-device", hardwareVersion)
    }

    @Test
    fun happyPath() {
        assertEquals(
            "brand-product-device",
            DeviceInfo.getLegacyHardwareVersion(
                mapOf(
                    "ro.product.brand" to "brand",
                    "ro.product.name" to "product",
                    "ro.product.device" to "device"
                )
            )
        )
    }

    @Test
    fun missingValues() {
        assertEquals(
            "device",
            DeviceInfo.getLegacyHardwareVersion(
                mapOf(
                    "ro.product.device" to "device"
                )
            )
        )
    }
}
