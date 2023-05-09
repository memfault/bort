package com.memfault.bort.selfTesting

import com.memfault.bort.ReporterServiceConnector

class SelfTestReporterServiceConnect(
    val reporterServiceConnector: ReporterServiceConnector
) : SelfTester.Case {
    override suspend fun test(): Boolean {
        // Regression test for MFLT-3126
        reporterServiceConnector.connect { _ ->
            // Do nothing, don't call the client getter, which would awaits for the service to actually be connected.
            // Instead, returning immediately, will immediately cause an unbind() call, before the onServiceConnected()
            // callback has been called.
        }
        val version = reporterServiceConnector.connect { getClient ->
            val client = getClient()
            client.getVersion()
        }
        return version != null
    }
}
