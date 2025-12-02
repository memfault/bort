package com.memfault.bort.connectivity

import android.content.SharedPreferences
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LastConnectedNetwork(
    val bootId: String,
    val fingerprintingInfo: WifiFingerprintingInfo?,
    val frequency: Int,
)

interface LastConnectedNetworkStorage {
    var state: LastConnectedNetwork
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = LastConnectedNetworkStorage::class)
class RealLastConnectedNetworkStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
    readBootId: LinuxBootId,
) : LastConnectedNetworkStorage, SerializedCachedPreferenceKeyProvider<LastConnectedNetwork>(
    sharedPreferences = sharedPreferences,
    defaultValue = LastConnectedNetwork(
        bootId = readBootId(),
        fingerprintingInfo = null,
        frequency = -1,
    ),
    serializer = LastConnectedNetwork.serializer(),
    preferenceKey = "LAST_CONNECTED_NETWORK",
)
