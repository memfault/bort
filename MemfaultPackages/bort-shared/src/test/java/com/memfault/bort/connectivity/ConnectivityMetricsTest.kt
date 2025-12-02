package com.memfault.bort.connectivity

import assertk.assertThat
import assertk.assertions.isEqualTo
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
}
