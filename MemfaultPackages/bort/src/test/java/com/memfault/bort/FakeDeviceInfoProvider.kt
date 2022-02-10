package com.memfault.bort

class FakeDeviceInfoProvider : DeviceInfoProvider {
    override suspend fun getDeviceInfo(): DeviceInfo = DEVICE_INFO_FIXTURE
}

internal val DEVICE_INFO_FIXTURE = DeviceInfo("SN1234", "HW-FOO", "1.0.0")
