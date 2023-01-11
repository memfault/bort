package com.memfault.usagereporter.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.usagereporter.receivers.ConnectivityState.CELLULAR
import com.memfault.usagereporter.receivers.ConnectivityState.ETHERNET
import com.memfault.usagereporter.receivers.ConnectivityState.NONE
import com.memfault.usagereporter.receivers.ConnectivityState.UNKNOWN
import com.memfault.usagereporter.receivers.ConnectivityState.WIFI

class ConnectivityReceiver : BroadcastReceiver() {
    private val connectivityMetric = Reporting.report()
        .stateTracker<ConnectivityState>(name = "connectivity.type", aggregations = listOf(StateAgg.TIME_PER_HOUR))
    private val airplaneModeMetric = Reporting.report().boolStateTracker(name = "airplane_mode")
    private val validatedMetric = Reporting.report().boolStateTracker(name = "connectivity.validated")
    private val captivePortalMetric = Reporting.report().boolStateTracker(name = "connectivity.captive_portal")

    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
            val hasState = intent.hasExtra("state")
            if (!hasState) return
            val state = intent.getBooleanExtra("state", false)
            airplaneModeMetric.state(state)
        }
    }

    fun register(context: Context) {
        context.registerReceiver(
            ConnectivityReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
        )
        val connectivityService = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Not used (we get all the info we need from onCapabilitiesChanged, which follows this callback).
            }

            override fun onLost(network: Network) {
                // Always called before onAvailable/onCapabilitiesChanged in my testing.
                connectivityMetric.state(NONE)
                validatedMetric.state(false)
                captivePortalMetric.state(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // Dumb API hides getTransport() so we have to test each one individually (or use reflection..).
                val state = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CELLULAR
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ETHERNET
                    else -> UNKNOWN
                }
                connectivityMetric.state(state)
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                validatedMetric.state(validated)
                val captivePortal = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                captivePortalMetric.state(captivePortal)
            }
        }
        connectivityService.registerDefaultNetworkCallback(callback)

        val initialAirplaneMode = isAirplaneModeOn(context)
        airplaneModeMetric.state(initialAirplaneMode)
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }
}

enum class ConnectivityState {
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN,
    NONE,
}
