package com.memfault.bort.connectivity

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.provider.Settings.Global
import androidx.annotation.RequiresPermission
import com.memfault.bort.Default
import com.memfault.bort.android.NetworkCallbackEvent.OnAvailable
import com.memfault.bort.android.NetworkCallbackEvent.OnCapabilitiesChanged
import com.memfault.bort.android.NetworkCallbackEvent.OnLost
import com.memfault.bort.android.registerForDefaultNetworkCallback
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.connectivity.ConnectivityState.BLUETOOTH
import com.memfault.bort.connectivity.ConnectivityState.CELLULAR
import com.memfault.bort.connectivity.ConnectivityState.ETHERNET
import com.memfault.bort.connectivity.ConnectivityState.NONE
import com.memfault.bort.connectivity.ConnectivityState.UNKNOWN
import com.memfault.bort.connectivity.ConnectivityState.USB
import com.memfault.bort.connectivity.ConnectivityState.VPN
import com.memfault.bort.connectivity.ConnectivityState.WIFI
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg.LATEST_VALUE
import com.memfault.bort.reporting.StateAgg.TIME_PER_HOUR
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

const val CONNECTIVITY_TYPE_METRIC = "connectivity.type"

// Don't automatically include this in all apps using @ContributesMultibinding (it'll be included in OTA,
// and Bort has to conditionally enable it only if UsageReporter is not installed).
@Singleton
class ConnectivityMetrics
@Inject constructor(
    private val application: Application,
    private val bortEnabledProvider: BortEnabledProvider,
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager?,
    @Default private val defaultCoroutineContext: CoroutineContext,
) : Scoped {
    private val connectivityMetric = Reporting.report()
        .stateTracker<ConnectivityState>(
            name = CONNECTIVITY_TYPE_METRIC,
            aggregations = listOf(TIME_PER_HOUR, TIME_TOTALS),
        )
    private val airplaneModeMetric = Reporting.report().boolStateTracker(name = "airplane_mode")
    private val validatedMetric = Reporting.report().boolStateTracker(name = "connectivity.validated")
    private val captivePortalMetric = Reporting.report().boolStateTracker(name = "connectivity.captive_portal")

    private val roamingMetric = Reporting.report().boolStateTracker(name = "connectivity.roaming")
    private val meteredMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.metered", aggregations = listOf(LATEST_VALUE))
    private val unmeteredTemporarilyMetric =
        Reporting.report().boolStateTracker(name = "connectivity.unmetered_temporarily")

    private val wifiFrequencyMetric = Reporting.report().numberProperty(name = "connectivity.wifi.frequency")
    private val wifiLinkSpeedMbpsMetric = Reporting.report().numberProperty(name = "connectivity.wifi.link_speed_mbps")
    private val wifiSecurityMetric = Reporting.report().stringProperty(
        name = "connectivity.wifi.security_type",
        addLatestToReport = true,
    )
    private val wifiStandardVersionMetric = Reporting.report().stringProperty(
        name = "connectivity.wifi.standard_version",
        addLatestToReport = true,
    )
    private val wifiLostTxPacketsPerSecondMetric = Reporting.report().numberProperty(
        name = "connectivity.wifi.lost_tx_packets_per_second",
    )
    private val wifiRetriedTxPacketsPerSecondMetric = Reporting.report().numberProperty(
        name = "connectivity.wifi.retried_tx_packets_per_second",
    )
    private val wifiSuccessfulTxPacketsPerSecond = Reporting.report().numberProperty(
        name = "connectivity.wifi.successful_tx_packets_per_second",
    )
    private val wifiSuccessfulRxPacketsPerSecond = Reporting.report().numberProperty(
        name = "connectivity.wifi.successful_rx_packets_per_second",
    )

    private val replaySettingsFlow = MutableSharedFlow<Unit>()

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope(defaultCoroutineContext).launch {
            registerAirplaneMode()
            registerConnectivity()
        }
    }

    override fun onExitScope() = Unit

    /**
     * Triggers a re-emission of the latest connectivity event.
     */
    suspend fun replayLatest() {
        replaySettingsFlow.emit(Unit)
    }

    private fun CoroutineScope.registerAirplaneMode() {
        bortEnabledProvider.isEnabledFlow()
            .distinctUntilChanged()
            .flatMapLatest { bortEnabled ->
                Logger.test("Listening for ACTION_AIRPLANE_MODE_CHANGED: $bortEnabled")
                if (bortEnabled) {
                    application.registerForIntents(ACTION_AIRPLANE_MODE_CHANGED)
                } else {
                    emptyFlow()
                }
            }
            .flowOn(defaultCoroutineContext)
            .flatMapLatest { intent -> replaySettingsFlow.map { intent }.onStart { emit(intent) } }
            .onEach { intent ->
                val hasState = intent.hasExtra("state")
                if (!hasState) return@onEach
                val state = intent.getBooleanExtra("state", false)
                airplaneModeMetric.state(state)
            }
            .launchIn(this)

        val initialAirplaneMode = isAirplaneModeOn(application)
        airplaneModeMetric.state(initialAirplaneMode)
    }

    @SuppressLint("MissingPermission")
    private fun CoroutineScope.registerConnectivity() {
        bortEnabledProvider.isEnabledFlow()
            .distinctUntilChanged()
            .flatMapLatest { bortEnabled ->
                Logger.test("Listening for NetworkCallback: $bortEnabled")
                if (bortEnabled) {
                    connectivityManager.registerForDefaultNetworkCallback()
                } else {
                    emptyFlow()
                }
            }
            .flatMapLatest { event -> replaySettingsFlow.map { event }.onStart { emit(event) } }
            .onEach { event ->
                when (event) {
                    is OnAvailable -> {
                        // Not used (we get all the info we need from onCapabilitiesChanged,
                        // which follows this callback).
                    }

                    is OnLost -> {
                        // Always called before onAvailable/onCapabilitiesChanged in my testing.
                        recordNetworkLost(event.network)
                    }

                    is OnCapabilitiesChanged -> {
                        recordNetworkCapabilities(event.networkCapabilities)
                    }
                }
            }
            .launchIn(this)

        val defaultNetwork = connectivityManager.activeNetwork
        val defaultNetworkCapabilities = connectivityManager.getNetworkCapabilities(defaultNetwork)
        if (defaultNetworkCapabilities != null) {
            recordNetworkCapabilities(defaultNetworkCapabilities)
        } else {
            recordNetworkLost(defaultNetwork)
        }
    }

    @RequiresPermission(permission.ACCESS_NETWORK_STATE)
    private fun recordNetworkLost(network: Network?) {
        connectivityMetric.state(NONE)
        validatedMetric.state(false)
        captivePortalMetric.state(false)

        meteredMetric.state(false)
        if (Build.VERSION.SDK_INT >= 28) {
            roamingMetric.state(false)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            unmeteredTemporarilyMetric.state(false)
        }

        if (network != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                reportWifiInfo(null)
            }
        }

        logAsJson(state = NONE, validated = false, captivePortal = false)
    }

    private fun recordNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
        // Dumb API hides getTransport() so we have to test each one individually
        // (or use reflection..).
        val state = when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CELLULAR
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> BLUETOOTH
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ETHERNET
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> VPN
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> USB
            else -> UNKNOWN
        }
        connectivityMetric.state(state)

        reportWifiInfo(networkCapabilities.toWifiInfo(wifiManager))

        val validated =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        validatedMetric.state(validated)

        val captivePortal =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        captivePortalMetric.state(captivePortal)

        val isMetered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        meteredMetric.state(isMetered)

        if (Build.VERSION.SDK_INT >= 30) {
            val isUnmeteredTemporarily =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
            unmeteredTemporarilyMetric.state(isUnmeteredTemporarily)
        }

        if (Build.VERSION.SDK_INT >= 28) {
            val isRoaming = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            roamingMetric.state(isRoaming)
        }

        logAsJson(state = state, validated = validated, captivePortal = captivePortal)
    }

    private fun reportWifiInfo(wifiInfo: WifiInfo?) {
        wifiFrequencyMetric.update(wifiInfo?.frequency)
        wifiLinkSpeedMbpsMetric.update(wifiInfo?.linkSpeed)
        wifiLostTxPacketsPerSecondMetric.update(wifiInfo?.getLostTxPacketsPerSecondReflectively())
        wifiRetriedTxPacketsPerSecondMetric.update(wifiInfo?.getRetriedTxPacketsPerSecondReflectively())
        wifiSuccessfulRxPacketsPerSecond.update(wifiInfo?.getSuccessfulRxPacketsPerSecondReflectively())
        wifiSuccessfulTxPacketsPerSecond.update(wifiInfo?.getSuccessfulTxPacketsPerSecondReflectively())

        // Android 11+ has a new API to get the wifi standard
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            wifiStandardVersionMetric.update(humanReadableWifiStandard(wifiInfo?.wifiStandard))
        }

        // Android 12+ has a new API to get the wifi security type
        if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
            wifiSecurityMetric.update(humanReadableWifiSecurityType(wifiInfo?.currentSecurityType))
        }
    }

    private fun humanReadableWifiSecurityType(currentSecurityType: Int?) = when (currentSecurityType) {
        WifiInfo.SECURITY_TYPE_OPEN -> "Open"
        WifiInfo.SECURITY_TYPE_WEP -> "WEP"
        WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2 PSK"
        WifiInfo.SECURITY_TYPE_EAP -> "WPA/WPA2 EAP"
        WifiInfo.SECURITY_TYPE_SAE -> "WPA3 SAE"
        WifiInfo.SECURITY_TYPE_OWE -> "WPA3 OWE"
        WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI PSK"
        WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI CERT"
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3 Enterprise"
        WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3 Enterprise 192-bit"
        WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2 -> "Passpoint R1/R2"
        WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint R3"
        WifiInfo.SECURITY_TYPE_DPP -> "DPP (Wi-Fi Easy Connect)"
        null -> null
        else -> "unknown ($currentSecurityType)"
    }

    private fun humanReadableWifiStandard(standard: Int?) = when (standard) {
        ScanResult.WIFI_STANDARD_LEGACY -> "802.11abg"
        ScanResult.WIFI_STANDARD_11N -> "802.11n"
        ScanResult.WIFI_STANDARD_11AC -> "802.11ac"
        ScanResult.WIFI_STANDARD_11AD -> "802.11ad"
        ScanResult.WIFI_STANDARD_11AX -> "802.11ax"
        ScanResult.WIFI_STANDARD_11BE -> "802.11be"
        null -> null
        else -> "unknown ($standard)"
    }

    private fun logAsJson(
        state: ConnectivityState,
        validated: Boolean,
        captivePortal: Boolean,
    ) {
        Logger.test(
            """{"json_log": "connectivity", "state": "$state", """ +
                """"validated": $validated, "captive_portal": $captivePortal}""",
        )
    }

    private fun isAirplaneModeOn(context: Context): Boolean =
        Global.getInt(context.contentResolver, Global.AIRPLANE_MODE_ON, 0) != 0
}

private fun NetworkCapabilities.toWifiInfo(wifiManager: WifiManager?): WifiInfo? =
    if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
        transportInfo as? WifiInfo
    } else {
        @Suppress("DEPRECATION")
        wifiManager?.connectionInfo
    }

enum class ConnectivityState {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    VPN,
    USB,
    UNKNOWN,
    NONE,
}
