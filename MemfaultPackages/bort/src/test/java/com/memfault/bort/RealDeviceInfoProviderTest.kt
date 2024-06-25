package com.memfault.bort

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.AndroidBuildFormat
import com.memfault.bort.settings.AndroidBuildFormat.SYSTEM_PROPERTY_ONLY
import com.memfault.bort.settings.DeviceInfoSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RealDeviceInfoProviderTest {
    companion object {
        private const val HW_VERSION_BEFORE = "HW_BEFORE"
        private const val HW_KEY_BEFORE = "hardware_key_before"
        private const val HW_VERSION_AFTER = "HW_AFTER"
        private const val HW_KEY_AFTER = "hardware_key_after"
        private const val SERIAL_KEY = "serial_key"
        private const val SERIAL = "serial"
    }

    private var hardwareVersionKey = HW_KEY_BEFORE
    private val deviceInfoSettings = object : DeviceInfoSettings {
        override val androidBuildFormat: AndroidBuildFormat
            get() = SYSTEM_PROPERTY_ONLY
        override val androidBuildVersionKey: String
            get() = "build_key"
        override val androidHardwareVersionKey: String
            get() = hardwareVersionKey
        override val androidSerialNumberKey: String
            get() = "serial_key"
    }
    private val sysprops = mutableMapOf(
        HW_KEY_BEFORE to HW_VERSION_BEFORE,
        HW_KEY_AFTER to HW_VERSION_AFTER,
        SERIAL_KEY to SERIAL,
    )
    private val dumpsterClient: DumpsterClient = mockk {
        coEvery { getprop() } answers { sysprops }
    }
    private val application: Application = mockk()
    private var serial: String? = null
    private val overrideSerial = object : OverrideSerial {
        override var overriddenSerial: String?
            get() = serial
            set(_) {}
    }
    private val deviceInfoProvider = RealDeviceInfoProvider(
        deviceInfoSettings = deviceInfoSettings,
        dumpsterClient = dumpsterClient,
        application = application,
        overrideSerial = overrideSerial,
    )

    @Test
    fun cacheInvalidated_hardwareVersion() = runTest {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        assertThat(deviceInfo.hardwareVersion).isEqualTo(HW_VERSION_BEFORE)

        hardwareVersionKey = HW_KEY_AFTER

        val deviceInfoAfter = deviceInfoProvider.getDeviceInfo()
        assertThat(deviceInfoAfter.hardwareVersion).isEqualTo(HW_VERSION_AFTER)
    }

    @Test
    fun cacheInvalidated_serialOverride() = runTest {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        assertThat(deviceInfo.deviceSerial).isEqualTo(SERIAL)

        serial = "override"

        val deviceInfoAfter = deviceInfoProvider.getDeviceInfo()
        assertThat(deviceInfoAfter.deviceSerial).isEqualTo("override")
    }
}
