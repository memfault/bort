package com.memfault.bort

import android.os.RemoteException
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.memfault.bort.process.ProcessExecutor
import com.memfault.dumpster.IDumpster
import com.memfault.dumpster.IDumpsterBasicCommandListener
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DumpsterClientTest {
    private val processExecutor: ProcessExecutor = mockk {
        coEvery<String?> { execute(any(), any()) } answers { null }
    }

    @Test
    fun serviceNotAvailable() = runTest {
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster? = null
            },
            basicCommandTimeout = 5000,
            processExecutor = processExecutor,
        )
        assertThat(client.getprop()).isNull()
    }

    @Test
    fun minimumVersionNotSatisfied() = runTest {
        val service = mockk<IDumpster> {
            every { version } returns 0
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster = service
            },
            basicCommandTimeout = 5000,
            processExecutor = processExecutor,
        )
        assertThat(client.getprop()).isNull()
    }

    @Test
    fun binderRemoteException() = runTest {
        val service = mockk<IDumpster> {
            every { version } returns 1
            every { runBasicCommand(any(), any()) } throws RemoteException()
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster = service
            },
            basicCommandTimeout = 5000,
            processExecutor = processExecutor,
        )
        assertThat(client.getprop()).isNull()
        verify {
            service.runBasicCommand(any(), any())
        }
    }

    @Test
    fun responseTimeout() = runTest {
        val service = mockk<IDumpster> {
            every { version } returns 1
            every { runBasicCommand(any(), any()) } returns Unit
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster = service
            },
            basicCommandTimeout = 1,
            processExecutor = processExecutor,
        )
        assertThat(client.getprop()).isNull()
        verify {
            service.runBasicCommand(any(), any())
        }
    }

    @Test
    fun basicCommandUnsupported() = runTest {
        val service = mockk<IDumpster> {
            every { version } returns 1
            every { runBasicCommand(any(), any()) } answers {
                val listener: IDumpsterBasicCommandListener = secondArg()
                launch {
                    listener.onUnsupported()
                }
                Unit
            }
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster = service
            },
            basicCommandTimeout = 5000,
            processExecutor = processExecutor,
        )
        assertThat(client.getprop()).isNull()
        verify {
            service.runBasicCommand(any(), any())
        }
    }

    @Test
    fun getprop() = runTest {
        val service = mockk<IDumpster> {
            every { version } returns 1
            every { runBasicCommand(any(), any()) } answers {
                val listener: IDumpsterBasicCommandListener = secondArg()
                launch {
                    listener.onFinished(0, "[Hello]: [World!]")
                }
                Unit
            }
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster? = service
            },
            basicCommandTimeout = 5000,
            processExecutor = processExecutor,
        )
        assertThat(mapOf("Hello" to "World!")).isEqualTo(client.getprop())
    }
}
