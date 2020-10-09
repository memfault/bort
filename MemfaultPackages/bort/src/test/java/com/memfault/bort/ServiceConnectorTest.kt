package com.memfault.bort

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.lang.Exception

class MockService(val binder: IBinder)

interface MockServiceFactory {
    fun createServiceWithBinder(binder: IBinder): MockService
}

class ServiceConnectorTest {
    val componentName = ComponentName("pkg", "cls")
    lateinit var mockContext: Context
    lateinit var mockServiceFactory: MockServiceFactory
    lateinit var serviceConnector: TestServiceConnector
    var connection: ServiceConnection? = null

    inner class TestServiceConnector(
        context: Context, componentName: ComponentName
    ) : ServiceConnector<MockService>(context, componentName) {
        override fun createServiceWithBinder(binder: IBinder): MockService {
            return mockServiceFactory.createServiceWithBinder(binder)
        }
    }

    @Before
    fun setUp() {
        mockContext = mockk {
            val connectionSlot = slot<ServiceConnection>()
            every {
                bindService(any(), capture(connectionSlot), Context.BIND_AUTO_CREATE )
            } answers {
                connection = connectionSlot.captured
                true
            }
            every {
                unbindService(any())
            } returns Unit
        }
        mockServiceFactory = mockk {
            val slot = slot<IBinder>()
            every { createServiceWithBinder(capture(slot)) } answers { MockService(slot.captured) }
        }
        serviceConnector = TestServiceConnector(mockContext, componentName)
    }

    @Test
    fun multipleConnectCallsBindsUnbindsOnce() {
        runBlocking {
            val numClients = 3
            val channels = (1..numClients).map {
                Channel<Unit>().also { channel ->
                    launch {
                        serviceConnector.connect {
                            channel.send(Unit)
                            channel.receive()
                        }
                        channel.send(Unit)
                    }
                }
            }
            // Wait until each of the clients is suspended in the connect {} block:
            channels.forEach { it.receive() }
            verify(exactly = 1) { mockContext.bindService(any(), any(), any()) }
            // Wait until each client returns from of the connect {} block:
            channels.forEach {
                it.send(Unit)
                it.receive()
            }
            verify(exactly = 1) { mockContext.unbindService(any()) }
        }
    }

    @Test
    fun handlesRemoteExceptionDuringBind() {
        every {
            mockContext.bindService(any(), any(), any())
        } throws RemoteException()

        var connectCalled = false
        var caughtException = false
        runBlocking {
            try {
                serviceConnector.connect {
                    connectCalled = true
                }
            } catch (e: RemoteException) {
                caughtException = true
            }
        }
        assertEquals(false, connectCalled)
        assertEquals(true, caughtException)
        assertEquals(false, serviceConnector.hasClients)
        assertEquals(false, serviceConnector.bound)
    }

    @Test
    fun handlesExceptionInConnectBlock() {
        var connectCalled = false
        runBlocking {
            try {
                serviceConnector.connect<Unit> {
                    connectCalled = true
                    throw Exception("Boom!")
                }
            } catch (e: Exception) {}
        }
        assertEquals(true, connectCalled)
        assertEquals(false, serviceConnector.hasClients)
        assertEquals(false, serviceConnector.bound)
        verify(exactly = 1) { mockContext.unbindService(any()) }
    }

    @Test
    fun serviceDisconnectReconnect() {
        runBlocking {
            val iBinder1 = mockk<IBinder>()
            val iBinder2 = mockk<IBinder>()

            var service1: MockService? = null
            var service2A: MockService? = null
            var service2B: MockService? = null

            // First 'A' client:
            val resultA = async {
                serviceConnector.connect { getService ->
                    service1 = getService()
                    yield()
                    service2A = getService()
                }
            }
            yield()
            assertNotNull(connection)

            connection?.onServiceConnected(componentName, iBinder1)
            yield()
            connection?.onServiceDisconnected(componentName)

            // Second 'B' client connects when service has just disconnected:
            val resultB = async {
                serviceConnector.connect { getService ->
                    service2B = getService()
                }
            }

            yield()
            connection?.onServiceConnected(componentName, iBinder2)
            yield()

            resultA.await()
            resultB.await()

            assertNotNull(service1)
            assertEquals(iBinder1, service1?.binder)
            assertNotNull(service2A)
            assertEquals(iBinder2, service2A?.binder)
            assertNotNull(service2B)
            assertEquals(iBinder2, service2B?.binder)
            assertEquals(false, serviceConnector.hasClients)
            assertEquals(false, serviceConnector.bound)
            verify(exactly = 1) { mockContext.bindService(any(), any(), any()) }
            verify(exactly = 1) { mockContext.unbindService(any()) }
        }
    }
}
