package com.memfault.bort

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import com.memfault.bort.shared.result.success
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReporterClientTest {
    lateinit var mockConnection: ReporterServiceConnection
    lateinit var client: ReporterClient

    @BeforeEach
    fun setUp() {
        mockConnection = mockk()
        client = ReporterClient(mockConnection, mockk())
    }

    @Test
    fun getVersion() = runTest {
        coEvery {
            mockConnection.sendAndReceive(VersionRequest())
        } coAnswers {
            Result.success(VersionResponse(123))
        }
        for (x in 1..3) {
            assertEquals(123, client.getVersion())
        }
        // Version is cached for subsequent getVersion() calls:
        coVerify(exactly = 1) { mockConnection.sendAndReceive(VersionRequest()) }
    }

    @Test
    fun unsupportedVersion() = runTest {
        coEvery {
            mockConnection.sendAndReceive(VersionRequest())
        } coAnswers {
            Result.success(VersionResponse(0))
        }
        val result = client.setLogLevel(LogLevel.TEST)
        assertEquals(
            "Unsupported request for loglevel (0 < 4)",
            result.getError()?.message,
        )
    }
}
