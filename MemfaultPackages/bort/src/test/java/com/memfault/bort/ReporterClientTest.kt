package com.memfault.bort

import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReporterClientTest {
    lateinit var mockConnection: ReporterServiceConnection
    lateinit var client: ReporterClient

    @Before
    fun setUp() {
        mockConnection = mockk()
        client = ReporterClient(mockConnection, mockk())
    }

    @Test
    fun getVersion() {
        runBlocking {
            coEvery {
                mockConnection.sendAndReceive(VersionRequest())
            } coAnswers {
                VersionResponse(123)
            }
            for (x in 1..3) {
                assertEquals(123, client.getVersion())
            }
            // Version is cached for subsequent getVersion() calls:
            coVerify(exactly = 1) {  mockConnection.sendAndReceive(VersionRequest()) }
        }
    }

    @Test
    fun unsupportedVersion() {
        runBlocking {
            coEvery {
                mockConnection.sendAndReceive(VersionRequest())
            } coAnswers {
                VersionResponse(0)
            }
            assertEquals(false, client.dropBoxSetTagFilter(emptyList()))
        }
    }
}
