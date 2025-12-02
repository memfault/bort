package com.memfault.bort.connectivity

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.provider.Settings.Global
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import com.memfault.bort.Default
import com.memfault.bort.android.NetworkCallbackEvent.OnAvailable
import com.memfault.bort.android.NetworkCallbackEvent.OnCapabilitiesChanged
import com.memfault.bort.android.NetworkCallbackEvent.OnLinkPropertiesChanged
import com.memfault.bort.android.NetworkCallbackEvent.OnLost
import com.memfault.bort.android.registerForDefaultNetworkCallback
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.connectivity.ConnectivityState.BLUETOOTH
import com.memfault.bort.connectivity.ConnectivityState.CELLULAR
import com.memfault.bort.connectivity.ConnectivityState.ETHERNET
import com.memfault.bort.connectivity.ConnectivityState.NONE
import com.memfault.bort.connectivity.ConnectivityState.UNKNOWN
import com.memfault.bort.connectivity.ConnectivityState.USB
import com.memfault.bort.connectivity.ConnectivityState.VPN
import com.memfault.bort.connectivity.ConnectivityState.WIFI
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
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
import java.net.Inet4Address
import java.net.Inet6Address
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
    private val wifiInfoProvider: WifiFingerprintingInfoProvider,
    private val lastConnectedNetworkStorage: LastConnectedNetworkStorage,
    private val readBootId: LinuxBootId,
) : Scoped {
    private val currentBootId: String by lazy { readBootId() }
    private val connectivityMetric = Reporting.report()
        .stateTracker<ConnectivityState>(
            name = CONNECTIVITY_TYPE_METRIC,
            aggregations = listOf(TIME_PER_HOUR, TIME_TOTALS),
        )
    private val airplaneModeMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.airplane_mode", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val internetMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.internet", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val validatedMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.validated", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val captivePortalMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.captive_portal", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val roamingMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.roaming", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val meteredMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.metered", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val unmeteredTemporarilyMetric = Reporting.report()
        .boolStateTracker(name = "connectivity.unmetered_temporarily", aggregations = listOf(StateAgg.LATEST_VALUE))

    private val ipVersion = Reporting.report().stringStateTracker(
        name = "connectivity.ip_version",
        aggregations = listOf(StateAgg.LATEST_VALUE),
    )
    private val ipv4Status = Reporting.report().stringStateTracker(
        name = "connectivity.ipv4_status",
        aggregations = listOf(StateAgg.LATEST_VALUE),
    )
    private val ipv6Status = Reporting.report().stringStateTracker(
        name = "connectivity.ipv6_status",
        aggregations = listOf(StateAgg.LATEST_VALUE),
    )

    private val wifiFrequencyMetric = Reporting.report().distribution(
        name = "connectivity.wifi.frequency",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
    )
    private val wifiFrequencyBandMetric = Reporting.report().stringStateTracker(
        name = "connectivity.wifi.frequency_band",
        aggregations = listOf(StateAgg.LATEST_VALUE),
    )
    private val wifiLinkSpeedMbpsMetric = Reporting.report().distribution(
        name = "connectivity.wifi.link_speed_mbps",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
    )
    private val wifiSecurityMetric = Reporting.report().stringStateTracker(
        name = "connectivity.wifi.security_type",
        aggregations = listOf(StateAgg.LATEST_VALUE),
    )
    private val wifiStandardVersionMetric = Reporting.report().stringStateTracker(
        name = "connectivity.wifi.standard_version",
        aggregations = listOf(StateAgg.LATEST_VALUE),
    )
    private val wifiLostTxPacketsPerSecondMetric = Reporting.report().distribution(
        name = "connectivity.wifi.lost_tx_packets_per_second",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
    )
    private val wifiRetriedTxPacketsPerSecondMetric = Reporting.report().distribution(
        name = "connectivity.wifi.retried_tx_packets_per_second",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
    )
    private val wifiSuccessfulTxPacketsPerSecond = Reporting.report().distribution(
        name = "connectivity.wifi.successful_tx_packets_per_second",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
    )
    private val wifiSuccessfulRxPacketsPerSecond = Reporting.report().distribution(
        name = "connectivity.wifi.successful_rx_packets_per_second",
        aggregations = listOf(NumericAgg.LATEST_VALUE),
    )
    private val wifiOui = Reporting.report().stringProperty(
        name = "connectivity.wifi.ap_oui",
        addLatestToReport = true,
    )
    private val wifiRoamingCount = Reporting.report().counter(
        name = "connectivity.wifi.roaming_count",
        sumInReport = true,
    )
    private val wifiChannelHopCount = Reporting.report().counter(
        name = "connectivity.wifi.channel_hop_count",
        sumInReport = true,
    )

    private val replaySettingsFlow = MutableSharedFlow<Unit>()

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope(defaultCoroutineContext).launch {
            registerAirplaneMode()
            registerConnectivity()

            replaySettingsFlow
                .onEach { recordNetworkDefaults() }
                .launchIn(this)

            recordNetworkDefaults()
        }
    }

    override fun onExitScope() = Unit

    /**
     * Triggers a re-emission of the latest connectivity event.
     */
    suspend fun replayLatest() {
        replaySettingsFlow.emit(Unit)
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordNetworkDefaults() {
        val defaultNetwork = connectivityManager.activeNetwork
        val defaultNetworkCapabilities = connectivityManager.getNetworkCapabilities(defaultNetwork)
        if (defaultNetworkCapabilities != null) {
            recordNetworkCapabilities(defaultNetworkCapabilities)
        } else {
            recordNetworkLost(defaultNetwork)
        }
        val defaultLinkProperties = connectivityManager.getLinkProperties(defaultNetwork)
        recordLinkProperties(defaultLinkProperties)

        airplaneModeMetric.state(isAirplaneModeOn(application))
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

                    is OnLinkPropertiesChanged -> {
                        recordLinkProperties(event.linkProperties)
                    }
                }
            }
            .launchIn(this)
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
                reportWifiInfo(wifiInfo = null)
            }
        }

        ipVersion.state(null)
        ipv4Status.state(null)
        ipv6Status.state(null)

        logAsJson(state = NONE, validated = false, captivePortal = false)
    }

    private fun recordLinkProperties(linkProperties: LinkProperties?) {
        val ipv4Addresses = linkProperties?.linkAddresses?.mapNotNull { it.address as? Inet4Address }.orEmpty()
        val ipv6Addresses = linkProperties?.linkAddresses?.mapNotNull { it.address as? Inet6Address }.orEmpty()
        val hasIpv4 = ipv4Addresses.isNotEmpty()
        val hasIpv6 = ipv6Addresses.isNotEmpty()
        ipVersion.state(
            when {
                hasIpv4 && hasIpv6 -> "IPv4+IPv6"
                hasIpv4 -> "IPv4"
                hasIpv6 -> "IPv6"
                else -> "?"
            },
        )

        val hasRoutableIPv4 = ipv4Addresses.any { !it.isLinkLocalAddress && !it.isLoopbackAddress }
        val hasLinkLocalIPv4 = ipv4Addresses.any { it.isLinkLocalAddress }
        ipv4Status.state(
            when {
                hasRoutableIPv4 -> "Internet Available"
                hasLinkLocalIPv4 -> "Configuration Error"
                else -> "Not Available"
            },
        )

        val hasGlobalIPv6 = ipv6Addresses.any { !isNonGlobalIPv6(it) }
        val hasIPv6 = ipv6Addresses.isNotEmpty()
        ipv6Status.state(
            when {
                !hasIPv6 -> "Not Available"
                hasGlobalIPv6 -> "Internet Available"
                else -> "Local Only"
            },
        )
    }

    private fun isNonGlobalIPv6(address: Inet6Address): Boolean = address.isLoopbackAddress ||
        // ::1
        address.isLinkLocalAddress ||
        // fe80::/10
        address.isSiteLocalAddress ||
        // fec0::/10 (deprecated)
        isIPv6UniqueLocal(address) ||
        // fc00::/7
        isIPv6Multicast(address) ||
        // ff00::/8
        isIPv6Documentation(address) ||
        // 2001:db8::/32
        isIPv6Reserved(address)

    private fun isIPv6Reserved(address: Inet6Address): Boolean {
        val bytes = address.address

        // 0000::/16 - mostly reserved (except ::1 which is handled separately)
        if (bytes[0] == 0x00.toByte()) return true

        // 0100::/64 - Discard prefix
        if (bytes[0] == 0x01.toByte() && bytes[1] == 0x00.toByte()) return true

        return false
    }

    private fun isIPv6UniqueLocal(address: Inet6Address): Boolean {
        val firstByte = address.address[0].toInt() and 0xFF
        return (firstByte and 0xFE) == 0xFC // fc00::/7 (both fc00::/8 and fd00::/8)
    }

    private fun isIPv6Multicast(address: Inet6Address): Boolean {
        val firstByte = address.address[0].toInt() and 0xFF
        // Multicast check (ff00::/8)
        return firstByte == 0xFF
    }

    private fun isIPv6Documentation(address: Inet6Address): Boolean {
        val bytes = address.address
        // Documentation prefix check (2001:db8::/32)
        return bytes[0] == 0x20.toByte() &&
            bytes[1] == 0x01.toByte() &&
            bytes[2] == 0x0d.toByte() &&
            bytes[3] == 0xb8.toByte()
    }

    private suspend fun recordNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
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

        val connectedWifiInfo = wifiInfoProvider.getWifiFingerprintingInfo()

        reportWifiInfo(
            wifiInfo = networkCapabilities.toWifiInfo(wifiManager),
            connectedWifiInfo = connectedWifiInfo,
        )

        val internet =
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        internetMetric.state(internet)

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

    private fun reportWifiInfo(wifiInfo: WifiInfo?, connectedWifiInfo: WifiFingerprintingInfo? = null) {
        wifiInfo?.frequency?.let { frequency ->
            wifiFrequencyMetric.record(frequency.toLong())

            wifiFrequencyBandMetric.state(
                when {
                    wifiInfo.is24GHzReflectively() == true -> "2.4"
                    wifiInfo.is5GHzReflectively() == true -> "5"
                    wifiInfo.is6GHzReflectively() == true -> "6"
                    else -> null
                },
            )
        }
        wifiInfo?.linkSpeed?.let { wifiLinkSpeedMbpsMetric.record(it.toLong()) }
        wifiInfo?.getLostTxPacketsPerSecondReflectively()?.let { wifiLostTxPacketsPerSecondMetric.record(it) }
        wifiInfo?.getRetriedTxPacketsPerSecondReflectively()?.let { wifiRetriedTxPacketsPerSecondMetric.record(it) }
        wifiInfo?.getSuccessfulRxPacketsPerSecondReflectively()?.let { wifiSuccessfulRxPacketsPerSecond.record(it) }
        wifiInfo?.getSuccessfulTxPacketsPerSecondReflectively()?.let { wifiSuccessfulTxPacketsPerSecond.record(it) }

        val oui = connectedWifiInfo?.bssid?.let { bssidToOui(it) }
        wifiOui.update(oui ?: "")

        reportWifiHoppingAndRoaming(
            wifiInfo = wifiInfo,
            connectedWifiInfo = connectedWifiInfo,
        )

        // Android 11+ has a new API to get the wifi standard
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            wifiStandardVersionMetric.state(humanReadableWifiStandard(wifiInfo?.wifiStandard))
        }

        // Android 12+ has a new API to get the wifi security type
        if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
            wifiSecurityMetric.state(humanReadableWifiSecurityType(wifiInfo?.currentSecurityType))
        }
    }

    private fun reportWifiHoppingAndRoaming(wifiInfo: WifiInfo?, connectedWifiInfo: WifiFingerprintingInfo?) {
        // When are on the same network but changed bssid (AP) or channel, report roaming or hopping
        val lastPersistedNetwork = lastConnectedNetworkStorage.state
        if (lastPersistedNetwork.bootId == currentBootId &&
            lastPersistedNetwork.fingerprintingInfo != null &&
            connectedWifiInfo != null &&
            lastPersistedNetwork.fingerprintingInfo.networkId == connectedWifiInfo.networkId &&
            wifiInfo != null
        ) {
            if (lastPersistedNetwork.fingerprintingInfo.bssid != connectedWifiInfo.bssid) {
                wifiRoamingCount.increment()
            }
            if (lastPersistedNetwork.frequency != wifiInfo.frequency) {
                wifiChannelHopCount.increment()
            }
        }

        // Persist the current wifi info for future channel hopping / network change analysis
        lastConnectedNetworkStorage.state = LastConnectedNetwork(
            bootId = currentBootId,
            fingerprintingInfo = connectedWifiInfo,
            frequency = wifiInfo?.frequency ?: -1,
        )
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

/**
 * Converts a BSSID to an OUI (Organizationally Unique Identifier).
 * The OUI is the first 3 bytes of the BSSID, represented as a hex string.
 */
@VisibleForTesting
internal fun bssidToOui(bssid: String): String =
    bssid.substring(0, 8)

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
