package com.memfault.bort.selfTesting

import com.memfault.bort.DumpsterClient
import com.memfault.bort.settings.DeviceInfoSettings

class SelfTestDumpster(val deviceInfoSettings: DeviceInfoSettings) : SelfTester.Case {
    override suspend fun test(): Boolean =
        DumpsterClient().getprop()?.containsKey(
            deviceInfoSettings.androidSerialNumberKey
        ) ?: false
}
