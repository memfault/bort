package com.memfault.bort.selftest

import com.memfault.bort.DumpsterClient
import com.memfault.bort.settings.DeviceInfoSettings

class SelfTestDumpster(
    val deviceInfoSettings: DeviceInfoSettings,
    private val dumpsterClient: DumpsterClient,
) :
    SelfTester.Case {
    override suspend fun test(isBortLite: Boolean): Boolean {
        if (isBortLite) {
            // Not supported
            return true
        }
        return dumpsterClient.getprop()?.containsKey(
            deviceInfoSettings.androidSerialNumberKey,
        ) ?: false
    }
}
