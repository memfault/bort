package com.memfault.bort.connectivity

import kotlinx.serialization.Serializable

@Serializable
data class WifiFingerprintingInfo(
    val networkId: Int,
    val bssid: String,
) {
    companion object {
        const val INVALID_NETWORK_ID = -1
    }
}
