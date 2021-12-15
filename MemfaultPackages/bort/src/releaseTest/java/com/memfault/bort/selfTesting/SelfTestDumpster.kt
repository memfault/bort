package com.memfault.bort.selfTesting

import com.memfault.bort.DumpsterClient
import com.memfault.bort.settings.DeviceInfoSettings

class SelfTestDumpster(val deviceInfoSettings: DeviceInfoSettings, private val dumpsterClient: DumpsterClient) :
    SelfTester.Case {
    override suspend fun test(): Boolean =
        dumpsterClient.getprop()?.containsKey(
            deviceInfoSettings.androidSerialNumberKey
        ) ?: false
}
