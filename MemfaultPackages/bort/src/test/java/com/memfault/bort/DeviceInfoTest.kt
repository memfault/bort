package com.memfault.bort

import android.content.Context
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.AndroidBuildFormat
import io.mockk.mockk
import org.junit.Test

val TEST_BUILD_FINGERPRINT = "Sony/H8314/H8314:9/52.0.A.3.163/772046555:user/release-keys"
val fakeGetBuildFingerprint = { TEST_BUILD_FINGERPRINT }

class DeviceInfoFromSettingsAndProperties {
    private var settings = deviceInfoParams(AndroidBuildFormat.SYSTEM_PROPERTY_ONLY)
    private val context: Context = mockk()
    private val fallbackAndroidId = { "fallback" }

    private fun deviceInfoParams(buildFormat: AndroidBuildFormat) = DeviceInfoParams(
        androidBuildFormat = buildFormat,
        androidBuildVersionKey = "ro.build.version.incremental",
        androidHardwareVersionKey = "ro.product.model",
        androidSerialNumberKey = "ro.serialno",
        overriddenSerialNumber = null,
    )

    @Test
    fun happyPathHardwareAndDeviceSerial() {
        val props = mapOf(
            "ro.serialno" to "SERIAL",
            "ro.product.model" to "HARDWARE-XYZ",
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings,
            props,
            context,
            getFallbackAndroidId = fallbackAndroidId,
        )
        assertThat(deviceInfo.deviceSerial).isEqualTo("SERIAL")
        assertThat(deviceInfo.hardwareVersion).isEqualTo("HARDWARE-XYZ")
    }

    @Test
    fun happyPathHardwareAndDeviceSerial_overrideSerial() {
        val props = mapOf(
            "ro.serialno" to "SERIAL",
            "ro.product.model" to "HARDWARE-XYZ",
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings.copy(overriddenSerialNumber = "OVERRIDE"),
            props,
            context,
            getFallbackAndroidId = fallbackAndroidId,
        )
        assertThat(deviceInfo.deviceSerial).isEqualTo("OVERRIDE")
        assertThat(deviceInfo.hardwareVersion).isEqualTo("HARDWARE-XYZ")
    }

    @Test
    fun happyPathSoftwareVersionSystemPropertyOnly() {
        settings = deviceInfoParams(AndroidBuildFormat.SYSTEM_PROPERTY_ONLY)
        val props = mapOf(
            "ro.build.version.incremental" to "123",
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings,
            props,
            context,
            getFallbackAndroidId = fallbackAndroidId,
        )
        assertThat(deviceInfo.softwareVersion).isEqualTo("123")
    }

    @Test
    fun happyPathSoftwareVersionBuildFingerPrintOnly() {
        settings = deviceInfoParams(AndroidBuildFormat.BUILD_FINGERPRINT_ONLY)
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings,
            mapOf(),
            context,
            getBuildFingerprint = fakeGetBuildFingerprint,
            getFallbackAndroidId = fallbackAndroidId,
        )
        assertThat(deviceInfo.softwareVersion).isEqualTo(TEST_BUILD_FINGERPRINT)
    }

    @Test
    fun happyPathSoftwareVersionBuildFingerPrintAndSystemProperty() {
        settings = deviceInfoParams(AndroidBuildFormat.BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY)
        val props = mapOf(
            "ro.build.version.incremental" to "123",
        )
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings,
            props,
            context,
            getBuildFingerprint = fakeGetBuildFingerprint,
            getFallbackAndroidId = fallbackAndroidId,
        )
        assertThat(deviceInfo.softwareVersion).isEqualTo("$TEST_BUILD_FINGERPRINT::123")
    }

    @Test
    fun missingValues() {
        val deviceInfo = DeviceInfo.fromSettingsAndSystemProperties(
            settings,
            mapOf(),
            context,
            getFallbackAndroidId = fallbackAndroidId,
        )
        assertThat(deviceInfo.deviceSerial).isEqualTo("fallback")
        assertThat(deviceInfo.hardwareVersion).isEqualTo("unknown")
        assertThat(deviceInfo.softwareVersion).isEqualTo("unknown")
    }
}

class DeviceInfoLegacy {
    private val settings = DeviceInfoParams(
        androidBuildFormat = AndroidBuildFormat.SYSTEM_PROPERTY_ONLY,
        androidBuildVersionKey = "ro.build.version.incremental",
        androidHardwareVersionKey = "",
        androidSerialNumberKey = "ro.serialno",
        overriddenSerialNumber = null,
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
                "ro.product.device" to "device",
            ),
        )
        assertThat(hardwareVersion).isEqualTo("brand-product-device")
    }

    @Test
    fun happyPath() {
        assertThat(
            DeviceInfo.getLegacyHardwareVersion(
                mapOf(
                    "ro.product.brand" to "brand",
                    "ro.product.name" to "product",
                    "ro.product.device" to "device",
                ),
            ),
        ).isEqualTo("brand-product-device")
    }

    @Test
    fun missingValues() {
        assertThat(
            DeviceInfo.getLegacyHardwareVersion(
                mapOf(
                    "ro.product.device" to "device",
                ),
            ),
        ).isEqualTo("device")
    }
}
