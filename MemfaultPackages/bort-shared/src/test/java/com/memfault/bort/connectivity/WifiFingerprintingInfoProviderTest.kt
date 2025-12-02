package com.memfault.bort.connectivity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class WifiFingerprintingInfoProviderTest {
    @Test fun `parses the output of dumpsys correctly`() {
        assertThat(
            WifiFingerprintingInfoProvider.parseFingerprintingInfoFromDumpsys(
                DUMPSYS_REDUCED_WIFI_OUTPUT.byteInputStream(),
            ),
        )
            .isEqualTo(EXPECTED_WIFI_INFO)
    }

    @Test fun `null when not found`() {
        assertThat(WifiFingerprintingInfoProvider.parseFingerprintingInfoFromDumpsys("".byteInputStream()))
            .isNull()
    }

    @Test fun `null when bssid not in expected format`() {
        val outputWithInvalidBssid = DUMPSYS_REDUCED_WIFI_OUTPUT.replace(EXPECTED_BSSID, "trash")
        assertThat(
            WifiFingerprintingInfoProvider.parseFingerprintingInfoFromDumpsys(outputWithInvalidBssid.byteInputStream()),
        )
            .isNull()
    }

    @Test fun `null when last network id is invalid`() {
        val outputWithInvalidBssid = DUMPSYS_REDUCED_WIFI_OUTPUT.replace("mLastNetworkId 0", "mLastNetworkId trash")
        assertThat(
            WifiFingerprintingInfoProvider.parseFingerprintingInfoFromDumpsys(outputWithInvalidBssid.byteInputStream()),
        )
            .isNull()
    }

    companion object {
        const val EXPECTED_BSSID = "00:13:10:85:fe:01"
        private val EXPECTED_WIFI_INFO = WifiFingerprintingInfo(
            networkId = 0,
            bssid = EXPECTED_BSSID,
        )
        private val DUMPSYS_REDUCED_WIFI_OUTPUT = """Dump of ClientModeImpl id=9401
WifiClientModeImpl:
 total records=57

mAuthFailureInSupplicantBroadcast false
mAuthFailureReason 0

mLinkProperties {InterfaceName: wlan0 LinkAddresses: [ fe80::61ef:58a:df58:fa6c/64,10.0.2.16/24,fec0::a9ca:e93b:2bef:eb0f/64,fec0::b359:a72e:29f7:661d/64 ] DnsAddresses: [ /10.0.2.3 ] Domains: null MTU: 0 ServerAddress: /10.0.2.2 TcpBufferSizes: 524288,1048576,2097152,262144,524288,1048576 Routes: [ fe80::/64 -> :: wlan0 mtu 0,::/0 -> fe80::2 wlan0 mtu 0,fec0::/64 -> :: wlan0 mtu 0,10.0.2.0/24 -> 0.0.0.0 wlan0 mtu 0,0.0.0.0/0 -> 10.0.2.2 wlan0 mtu 0 ]}
mWifiInfo SSID: "AndroidWifi", BSSID: 00:13:10:85:fe:01, MAC: 02:15:b4:00:00:00, IP: /10.0.2.16, Security type: 0, Supplicant state: COMPLETED, Wi-Fi standard: 1, RSSI: -50, Link speed: 1Mbps, Tx Link speed: 1Mbps, Max Supported Tx Link speed: 11Mbps, Rx Link speed: 2Mbps, Max Supported Rx Link speed: 11Mbps, Frequency: 2447MHz, Net ID: 0, Metered hint: false, score: 60, isUsable: true, CarrierMerged: false, SubscriptionId: -1, IsPrimary: 1, Trusted: true, Restricted: false, Ephemeral: false, OEM paid: false, OEM private: false, OSU AP: false, FQDN: <none>, Provider friendly name: <none>, Requesting package name: <none>"AndroidWifi"openMLO Information: , AP MLD Address: <none>, AP MLO Link Id: <none>, AP MLO Affiliated links: <none>
mDhcpResultsParcelable baseConfiguration IP address 10.0.2.16/24 Gateway 10.0.2.2  DNS servers: [ 10.0.2.3 ] Domains leaseDuration 86400mtu 0serverAddress 10.0.2.2serverHostName vendorInfo null
mLastSignalLevel 4
mLastTxKbps 12000
mLastRxKbps 30000
mLastBssid 00:13:10:85:fe:01
mLastNetworkId 0
mLastSubId -1
mLastSimBasedConnectionCarrierName null
mSuspendOptimizationsEnabled true
mSuspendOptNeedsDisabled 4
        """.trimIndent()
    }
}
