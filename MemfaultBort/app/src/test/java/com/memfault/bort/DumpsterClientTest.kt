package com.memfault.bort

import android.os.RemoteException
import com.memfault.dumpster.*
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class DumpsterClientTest {

    @Test
    fun serviceNotAvailable() {
        val client = DumpsterClient(serviceProvider = object : DumpsterServiceProvider {
            override fun get(): IDumpster? = null
        })
        runBlocking {
            assertNull(client.getprop())
        }
    }

    @Test
    fun minimumVersionNotSatisfied() {
        val service = mock<IDumpster> {
            on { getVersion() } doReturn 0
        }
        val client = DumpsterClient(serviceProvider = object : DumpsterServiceProvider {
            override fun get(): IDumpster? = service
        })
        runBlocking {
            assertNull(client.getprop())
        }
    }

    @Test
    fun binderRemoteExeption() {
        val service = mock<IDumpster> {
            on { getVersion() } doReturn 1
            on { runBasicCommand(any(), any()) } doAnswer {
                throw RemoteException()
            }
        }
        val client = DumpsterClient(serviceProvider = object : DumpsterServiceProvider {
            override fun get(): IDumpster? = service
        })
        runBlocking {
            assertNull(client.getprop())
        }
        verify(service).runBasicCommand(any(), any())
    }

    @Test
    fun responseTimeout() {
        val service = mock<IDumpster> {
            on { getVersion() } doReturn 1
            on { runBasicCommand(any(), any()) } doAnswer {}
        }
        val client = DumpsterClient(
            serviceProvider = object : DumpsterServiceProvider {
                override fun get(): IDumpster? = service
            },
            basicCommandTimeout = 1
        )
        runBlocking {
            assertNull(client.getprop())
        }
        verify(service).runBasicCommand(any(), any())
    }

    @Test
    fun basicCommandUnsupported() {
        runBlocking {
            val service = mock<IDumpster> {
                on { getVersion() } doReturn 1
                on { runBasicCommand(any(), any()) } doAnswer {
                    val listener: IDumpsterBasicCommandListener = it.arguments[1] as IDumpsterBasicCommandListener
                    launch {
                        listener.onUnsupported()
                    }
                    Unit
                }
            }
            val client = DumpsterClient(serviceProvider = object : DumpsterServiceProvider {
                override fun get(): IDumpster? = service
            })
            assertNull(client.getprop())
            verify(service).runBasicCommand(any(), any())
        }
    }

    @Test
    fun getprop() {
        runBlocking {
            val service = mock<IDumpster> {
                on { getVersion() } doReturn 1
                on { runBasicCommand(any(), any()) } doAnswer {
                    val listener: IDumpsterBasicCommandListener = it.arguments[1] as IDumpsterBasicCommandListener
                    launch {
                        listener.onFinished(0, "[Hello]: [World!]")
                    }
                    Unit
                }
            }
            val client = DumpsterClient(serviceProvider = object : DumpsterServiceProvider {
                override fun get(): IDumpster? = service
            })
            assertEquals(client.getprop(), mapOf("Hello" to "World!"))
        }
    }
}
