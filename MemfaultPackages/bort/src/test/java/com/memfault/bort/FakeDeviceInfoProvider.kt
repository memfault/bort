package com.memfault.bort

object FakeDeviceInfoProvider : DeviceInfoProvider {
    override suspend fun getDeviceInfo(): DeviceInfo =
        DeviceInfo("SN1234", "HW-FOO", "1.0.0")
}
