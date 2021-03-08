package com.memfault.bort

import com.memfault.bort.PackageManagerClient.Util.appIdGuessesFromProcessName
import com.memfault.bort.parsers.PackageManagerReport
import io.mockk.mockk
import kotlin.time.minutes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PackageManagerClientTest {
    lateinit var mockServiceConnector: ReporterServiceConnector
    lateinit var reporterClient: ReporterClient
    lateinit var client: PackageManagerClient

    @BeforeEach
    fun setUp() {
        reporterClient = mockk()
        mockServiceConnector = createMockServiceConnector(reporterClient)
        client = PackageManagerClient(mockServiceConnector, 1::minutes)
    }

    @Test
    fun getPackageManagerReportInvalidAppId() {
        runBlocking {
            assertEquals(PackageManagerReport(), client.getPackageManagerReport(appId = "bad app id"))
        }
    }

    @Test
    fun appIdGuessesFromProcessName() {
        // Valid app IDs must have at least one dot:
        assertEquals(
            listOf("com.memfault.smartsink.bort", "com.memfault.smartsink", "com.memfault"),
            appIdGuessesFromProcessName("com.memfault.smartsink.bort").toList()
        )

        assertEquals(
            emptyList<String>(),
            appIdGuessesFromProcessName("/system/bin/storaged").toList()
        )
    }
}
