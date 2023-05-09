package com.memfault.usagereporter.selftest

import com.memfault.bort.shared.Logger
import javax.inject.Inject

class InitializedTestCase
@Inject constructor() : ReporterSelfTestCase {
    override suspend fun test(): Boolean {
        Logger.test("Test cases initialized")
        return true
    }
}
