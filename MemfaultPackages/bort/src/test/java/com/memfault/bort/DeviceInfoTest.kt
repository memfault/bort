package com.memfault.bort

import com.memfault.bort.settings.AndroidBuildFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

val TEST_BUILD_FINGERPRINT = "Sony/H8314/H8314:9/52.0.A.3.163/772046555:user/release-keys"
val fakeGetBuildFingerprint = { TEST_BUILD_FINGERPRINT }

class DeviceInfoFromSettingsAndProperties {
    private var settings = deviceInfoParams(AndroidBuildFormat.SYSTEM_PROPERTY_ONLY)

    private fun deviceInfoParams(buildFormat: AndroidBuildFormat) = DeviceInfoParams(
        androidBuildFormat = buildFormat,
        androidBuildVersionKey = "ro.build.version.incremental",
        androidHardwareVersionKey = "ro.product.board",
        androidSerialNumberKey = "ro.serialno",
    )

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
        settings = deviceInfoParams(AndroidBuildFormat.SYSTEM_PROPERTY_ONLY)
        val props = mapOf(
            "ro.build.version.incremental" to "123"
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(settings, props)
        assertEquals("123", deviceInfo.softwareVersion)
    }

    @Test
    fun happyPathSoftwareVersionBuildFingerPrintOnly() {
        settings = deviceInfoParams(AndroidBuildFormat.BUILD_FINGERPRINT_ONLY)
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings, mapOf(), getBuildFingerprint = fakeGetBuildFingerprint
        )
        assertEquals(TEST_BUILD_FINGERPRINT, deviceInfo.softwareVersion)
    }

    @Test
    fun happyPathSoftwareVersionBuildFingerPrintAndSystemProperty() {
        settings = deviceInfoParams(AndroidBuildFormat.BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY)
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
    private val settings = DeviceInfoParams(
        androidBuildFormat = AndroidBuildFormat.SYSTEM_PROPERTY_ONLY,
        androidBuildVersionKey = "ro.build.version.incremental",
        androidHardwareVersionKey = "",
        androidSerialNumberKey = "ro.serialno",
    )

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
