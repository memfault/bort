package com.memfault.bort.connectivity

import androidx.annotation.VisibleForTesting
import com.memfault.bort.process.ProcessExecutor
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiFingerprintingInfoProvider @Inject constructor(
    private val processExecutor: ProcessExecutor,
) {
    suspend fun getWifiFingerprintingInfo(): WifiFingerprintingInfo? = processExecutor.execute(
        DUMPSYS_WIFI_COMMAND,
    ) { inputStream ->
        parseFingerprintingInfoFromDumpsys(inputStream)
    }

    companion object {
        private const val LAST_BSSID_PREFIX = "mLastBssid "
        private const val LAST_NETWORK_ID_PREFIX = "mLastNetworkId "
        private val WIFI_BSSID_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        private val WIFI_NETWORK_ID_REGEX = Regex("^-?\\d+$")

        private val DUMPSYS_WIFI_COMMAND = listOf(
            "dumpsys",
            "wifi",
        )

        @VisibleForTesting
        internal fun parseFingerprintingInfoFromDumpsys(dumpsysInputStream: InputStream): WifiFingerprintingInfo? {
            var bssid: String? = null
            var networkId: Int = -1

            for (line in dumpsysInputStream.bufferedReader().lineSequence()) {
                if (line.startsWith(LAST_BSSID_PREFIX)) {
                    val bssidSubstr = line.substring(LAST_BSSID_PREFIX.length).trim()
                    bssid = bssidSubstr.takeIf { it.matches(WIFI_BSSID_REGEX) }
                }
                if (line.startsWith(LAST_NETWORK_ID_PREFIX)) {
                    val networkIdSubstr = line.substring(LAST_NETWORK_ID_PREFIX.length).trim()
                    networkId = networkIdSubstr.takeIf { it.matches(WIFI_NETWORK_ID_REGEX) }?.toIntOrNull()
                        ?: WifiFingerprintingInfo.INVALID_NETWORK_ID
                }
            }

            if (networkId == WifiFingerprintingInfo.INVALID_NETWORK_ID || bssid == null) {
                return null
            }

            return WifiFingerprintingInfo(
                networkId = networkId,
                bssid = bssid,
            )
        }
    }
}
