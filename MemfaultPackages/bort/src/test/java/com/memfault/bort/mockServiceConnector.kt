package com.memfault.bort

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot

fun createMockServiceConnector(reporterClient: ReporterClient): ReporterServiceConnector {
    val connectBlockSlot = slot<suspend (getService: ServiceGetter<ReporterClient>) -> Any>()
    val mockServiceConnector: ReporterServiceConnector = mockk()
    coEvery {
        mockServiceConnector.connect(capture(connectBlockSlot))
    } coAnswers {
        connectBlockSlot.captured({ reporterClient })
    }
    return mockServiceConnector
}
