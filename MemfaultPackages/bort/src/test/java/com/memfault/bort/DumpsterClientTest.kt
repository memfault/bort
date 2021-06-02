package com.memfault.bort

import android.os.RemoteException
import com.memfault.dumpster.IDumpster
import com.memfault.dumpster.IDumpsterBasicCommandListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DumpsterClientTest {

    @Test
    fun serviceNotAvailable() {
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster? = null
            }
        )
        runBlocking {
            assertNull(client.getprop())
        }
    }

    @Test
    fun minimumVersionNotSatisfied() {
        val service = mockk<IDumpster> {
            every { getVersion() } returns 0
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster? = service
            }
        )
        runBlocking {
            assertNull(client.getprop())
        }
    }

    @Test
    fun binderRemoteExeption() {
        val service = mockk<IDumpster> {
            every { getVersion() } returns 1
            every { runBasicCommand(any(), any()) } throws RemoteException()
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster? = service
            }
        )
        runBlocking {
            assertNull(client.getprop())
        }
        verify {
            service.runBasicCommand(any(), any())
        }
    }

    @Test
    fun responseTimeout() {
        val service = mockk<IDumpster> {
            every { getVersion() } returns 1
            every { runBasicCommand(any(), any()) } returns Unit
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(logIfMissing: Boolean): IDumpster? = service
            },
            basicCommandTimeout = 1
        )
        runBlocking {
            assertNull(client.getprop())
        }
        verify {
            service.runBasicCommand(any(), any())
        }
    }

    @Test
    fun basicCommandUnsupported() {
        runBlocking {
            val service = mockk<IDumpster> {
                every { getVersion() } returns 1
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
                    override fun get(logIfMissing: Boolean): IDumpster? = service
                }
            )
            assertNull(client.getprop())
            verify {
                service.runBasicCommand(any(), any())
            }
        }
    }

    @Test
    fun getprop() {
        runBlocking {
            val service = mockk<IDumpster> {
                every { getVersion() } returns 1
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
                }
            )
            assertEquals(client.getprop(), mapOf("Hello" to "World!"))
        }
    }
}
