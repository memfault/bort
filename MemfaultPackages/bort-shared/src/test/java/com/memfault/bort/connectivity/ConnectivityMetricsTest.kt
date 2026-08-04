package com.memfault.bort.connectivity

import android.net.NetworkCapabilities
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.android.FakeDeviceFeatures
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class ConnectivityMetricsTest {
    @Test fun `OUI is correctly parsed from BSSID`() {
        val expectedMappings = mapOf(
            "00:11:22:33:44:55" to "00:11:22",
            "aa:bb:cc:dd:ee:ff" to "aa:bb:cc",
        )

        expectedMappings.forEach { (bssid, oui) ->
            val parsedOui = bssidToOui(bssid)
            assertThat(parsedOui).isEqualTo(oui)
        }
    }

    private fun networkCapabilities(transport: Int) = mockk<NetworkCapabilities> {
        every { hasTransport(any()) } answers { firstArg<Int>() == transport }
    }

    @Test fun `wifi transport without wifi hardware is not classified as wifi`() {
        val state = classifyConnectivityState(
            networkCapabilities(NetworkCapabilities.TRANSPORT_WIFI),
            FakeDeviceFeatures(hasWifi = false),
        )
        assertThat(state).isEqualTo(ConnectivityState.UNKNOWN)
    }

    @Test fun `wifi transport with wifi hardware is classified as wifi`() {
        val state = classifyConnectivityState(
            networkCapabilities(NetworkCapabilities.TRANSPORT_WIFI),
            FakeDeviceFeatures(hasWifi = true),
        )
        assertThat(state).isEqualTo(ConnectivityState.WIFI)
    }

    @Test fun `cellular transport without telephony hardware is not classified as cellular`() {
        val state = classifyConnectivityState(
            networkCapabilities(NetworkCapabilities.TRANSPORT_CELLULAR),
            FakeDeviceFeatures(hasTelephony = false),
        )
        assertThat(state).isEqualTo(ConnectivityState.UNKNOWN)
    }

    @Test fun `bluetooth transport without bluetooth hardware is not classified as bluetooth`() {
        val state = classifyConnectivityState(
            networkCapabilities(NetworkCapabilities.TRANSPORT_BLUETOOTH),
            FakeDeviceFeatures(hasBluetooth = false),
        )
        assertThat(state).isEqualTo(ConnectivityState.UNKNOWN)
    }

    @Test fun `ethernet transport is unaffected by device features`() {
        val state = classifyConnectivityState(
            networkCapabilities(NetworkCapabilities.TRANSPORT_ETHERNET),
            FakeDeviceFeatures(hasWifi = false, hasTelephony = false, hasBluetooth = false),
        )
        assertThat(state).isEqualTo(ConnectivityState.ETHERNET)
    }
}
