package com.memfault.bort.android

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class NetworkCallbackEvent {
    data class OnAvailable(val network: Network) : NetworkCallbackEvent()
    data class OnLost(val network: Network) : NetworkCallbackEvent()
    data class OnCapabilitiesChanged(
        val network: Network,
        val networkCapabilities: NetworkCapabilities,
    ) : NetworkCallbackEvent()
}

/**
 * If you use this method, make sure your app's AndroidManifest declares it uses the
 * [android.permission.ACCESS_NETWORK_STATE] permission.
 */
@SuppressLint("MissingPermission")
suspend fun ConnectivityManager.registerForDefaultNetworkCallback(): Flow<NetworkCallbackEvent> = callbackFlow {
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(NetworkCallbackEvent.OnAvailable(network))
        }

        override fun onLost(network: Network) {
            trySend(NetworkCallbackEvent.OnLost(network))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(NetworkCallbackEvent.OnCapabilitiesChanged(network, networkCapabilities))
        }
    }

    registerDefaultNetworkCallback(callback)

    awaitClose { unregisterNetworkCallback(callback) }
}
