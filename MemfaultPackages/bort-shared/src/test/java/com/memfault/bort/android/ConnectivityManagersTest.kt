package com.memfault.bort.android

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityManagersTest {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var callback: CapturingSlot<NetworkCallback>

    @BeforeEach
    fun setUp() {
        callback = slot()
        connectivityManager = mockk {
            coEvery { registerDefaultNetworkCallback(capture(callback)) } returns Unit
            coEvery { unregisterNetworkCallback(any<NetworkCallback>()) } returns Unit
        }
    }

    @Test
    fun registersOnAvailable() = runTest {
        val first = async(UnconfinedTestDispatcher(testScheduler)) {
            connectivityManager.registerForDefaultNetworkCallback().first()
        }

        val network = mockk<Network>()
        callback.captured.onAvailable(network)

        Assertions.assertEquals(first.await(), NetworkCallbackEvent.OnAvailable(network))
    }

    @Test
    fun registersOnLost() = runTest {
        val first = async(UnconfinedTestDispatcher(testScheduler)) {
            connectivityManager.registerForDefaultNetworkCallback().firstOrNull()
        }

        val network = mockk<Network>()
        callback.captured.onLost(network)

        Assertions.assertEquals(first.await(), NetworkCallbackEvent.OnLost(network))
    }

    @Test
    fun registersOnCapabilities() = runTest {
        val first = async(UnconfinedTestDispatcher(testScheduler)) {
            connectivityManager.registerForDefaultNetworkCallback().firstOrNull()
        }

        val network = mockk<Network>()
        val networkCapabilities = mockk<NetworkCapabilities>()
        callback.captured.onCapabilitiesChanged(network, networkCapabilities)

        Assertions.assertEquals(first.await(), NetworkCallbackEvent.OnCapabilitiesChanged(network, networkCapabilities))
    }

    @Test
    fun unregisters() = runTest {
        val first = async(UnconfinedTestDispatcher(testScheduler)) {
            connectivityManager.registerForDefaultNetworkCallback().firstOrNull()
        }

        first.cancel()

        coVerify { connectivityManager.unregisterNetworkCallback(callback.captured) }
    }
}
