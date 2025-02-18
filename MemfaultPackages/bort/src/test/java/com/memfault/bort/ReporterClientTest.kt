package com.memfault.bort

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
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
    fun getVersion() = runTest {
        coEvery {
            mockConnection.sendAndReceive(VersionRequest())
        } coAnswers {
            Result.success(VersionResponse(123))
        }
        for (x in 1..3) {
            assertThat(client.getVersion()).isEqualTo(123)
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
        assertThat(result.getError()?.message).isNotNull().isEqualTo(
            "Unsupported request for loglevel (0 < 4)",
        )
    }
}
